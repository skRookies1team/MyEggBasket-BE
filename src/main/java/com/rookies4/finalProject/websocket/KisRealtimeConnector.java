package com.rookies4.finalProject.websocket;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.service.KisAuthService;
import com.rookies4.finalProject.service.UserService;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisRealtimeConnector {

    private static final String REAL_WS_URL = "ws://ops.koreainvestment.com:21000";
    private static final String VIRTUAL_WS_URL = "ws://ops.koreainvestment.com:31000";
    private static final String CUST_TYPE = "P";
    private static final Long FALLBACK_USER_ID = 1L;

    // 구독 취소하기까지 유예시간
    private static final long DISCONNECT_GRACE_MS = 15_000L;

    private final KisAuthService kisAuthService;
    private final UserService userService;
    private final RealtimePriceBroadcaster broadcaster;

    private final ConnectionState real = new ConnectionState();
    private final ConnectionState virtual = new ConnectionState();

    private final ScheduledExecutorService disconnectScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "kis-disconnect-scheduler");
                t.setDaemon(true);
                return t;
            });

    // useVirtual(false/true) 각각 따로 관리 (key = stockCode)
    private final Map<String, ScheduledFuture<?>> realDisconnectTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> virtualDisconnectTasks = new ConcurrentHashMap<>();

    /**
     * 애플리케이션 종료 시 리소스 정리
     * - ScheduledExecutorService graceful shutdown
     * - WebSocket 세션 종료
     */
    @PreDestroy
    public void shutdown() {
        log.info("[KIS] 애플리케이션 종료 - 리소스 정리 시작");

        // 1. 스케줄러 종료
        disconnectScheduler.shutdown();
        try {
            if (!disconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[KIS] 스케줄러가 5초 내에 종료되지 않아 강제 종료합니다.");
                disconnectScheduler.shutdownNow();
            } else {
                log.info("[KIS] 스케줄러가 정상적으로 종료되었습니다.");
            }
        } catch (InterruptedException e) {
            log.error("[KIS] 스케줄러 종료 중 인터럽트 발생", e);
            disconnectScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 2. WebSocket 세션 종료
        closeState(real);
        closeState(virtual);

        log.info("[KIS] 리소스 정리 완료");
    }

    public void connectIfAbsent(boolean useVirtualServer, String stockCode) {
        // 혹시 이전에 "disconnect 예약"이 걸려있다면 먼저 취소
        cancelScheduledDisconnect(useVirtualServer, stockCode);

        ConnectionState st = useVirtualServer ? virtual : real;
        ensureConnected(useVirtualServer, st);

        // 이미 등록한 종목이면 중복 전송 X
        if (!st.subscribed.add(stockCode)) {
            log.info("[KIS] already subscribed stockCode={} (useVirtual={})", stockCode, useVirtualServer);
            return;
        }

        st.handler.sendSubscribe(st.session, stockCode);
        log.info("[KIS] subscribedCodes(useVirtual={})={}", useVirtualServer, st.subscribed);
    }

    /**
     * 즉시 disconnect(UNREGISTER/close)하지 않고 유예 후 처리.
     * 유예시간 내에 다시 구독이 오면 connectIfAbsent()에서 자동 cancel.
     */
    public void scheduleDisconnect(boolean useVirtualServer, String stockCode) {
        Map<String, ScheduledFuture<?>> taskMap = useVirtualServer ? virtualDisconnectTasks : realDisconnectTasks;

        // 이미 취소예약이 있으면 갱신하지 않음(중복 예약 방지)
        if (taskMap.containsKey(stockCode)) return;

        ScheduledFuture<?> future = disconnectScheduler.schedule(() -> {
            try {
                // 실행 시점에 "이 취소예약이 아직 유효한지" 확인
                ScheduledFuture<?> cur = taskMap.get(stockCode);
                if (cur == null) return;

                // 현재 취소예약을 제거하고, 실제 disconnect 수행
                taskMap.remove(stockCode);
                disconnectIfPresent(useVirtualServer, stockCode);
            } catch (Exception e) {
                log.warn("[KIS] scheduled disconnect failed. stockCode={} useVirtual={}", stockCode, useVirtualServer, e);
            }
        }, DISCONNECT_GRACE_MS, TimeUnit.MILLISECONDS);

        taskMap.put(stockCode, future);
        log.info("[KIS] disconnect scheduled after {}ms. stockCode={} useVirtual={}",
                DISCONNECT_GRACE_MS, stockCode, useVirtualServer);
    }

    public void cancelScheduledDisconnect(boolean useVirtualServer, String stockCode) {
        Map<String, ScheduledFuture<?>> taskMap = useVirtualServer ? virtualDisconnectTasks : realDisconnectTasks;

        ScheduledFuture<?> f = taskMap.remove(stockCode);
        if (f == null) return;

        boolean cancelled = f.cancel(false);
        log.info("[KIS] disconnect schedule cancelled. stockCode={} useVirtual={} cancelled={}",
                stockCode, useVirtualServer, cancelled);
    }

    /**
     * (내부용) 실제로 KIS에 UNREGISTER 보내고, 필요 시 세션 close
     */
    private void disconnectIfPresent(boolean useVirtualServer, String stockCode) {
        ConnectionState st = useVirtualServer ? virtual : real;

        if (!st.subscribed.remove(stockCode)) return;
        if (st.session != null && st.session.isOpen()) {
            st.handler.sendUnsubscribe(st.session, stockCode);
        }

        log.info("[KIS] subscribedCodes(useVirtual={})={}", useVirtualServer, st.subscribed);

        // 더 이상 구독 종목이 없으면 세션 닫기
        if (st.subscribed.isEmpty()) closeState(st);
    }

    private void ensureConnected(boolean useVirtualServer, ConnectionState st) {
        if (st.session != null && st.session.isOpen()) return;

        synchronized (st.lock) {
            if (st.session != null && st.session.isOpen()) return;

            User user = userService.getUserById(FALLBACK_USER_ID);

            String wsUrl = useVirtualServer ? VIRTUAL_WS_URL : REAL_WS_URL;
            String approvalKey = kisAuthService.issueApprovalKey(useVirtualServer, user);

            WebSocketSession session = tryHandshake(wsUrl, approvalKey);
            if (session == null) {
                log.warn("[KIS] handshake failed. reissue approvalKey and retry once. useVirtual={}", useVirtualServer);
                String newApprovalKey = kisAuthService.reissueApprovalKey(useVirtualServer, user);
                session = tryHandshake(wsUrl, newApprovalKey);
            }

            if (session == null) throw new IllegalStateException("KIS connect failed");

            st.session = session;
            st.handler = new KisRealtimeWebSocketHandler(approvalKey, CUST_TYPE, broadcaster);

            log.info("[KIS] connected(useVirtual={}) sessionId={}", useVirtualServer, session.getId());

            // 세션 재연결 시, 기존 구독 목록 재등록
            for (String code : st.subscribed) {
                st.handler.sendSubscribe(st.session, code);
            }
        }
    }

    private WebSocketSession tryHandshake(String wsUrl, String approvalKey) {
        try {
            WebSocketClient client = new StandardWebSocketClient();
            KisRealtimeWebSocketHandler handler = new KisRealtimeWebSocketHandler(approvalKey, CUST_TYPE, broadcaster);
            return client.execute(handler, wsUrl).get();
        } catch (Exception e) {
            log.error("[KIS] 연결 실패. msg={}", e.getMessage(), e);
            return null;
        }
    }

    private void closeState(ConnectionState st) {
        synchronized (st.lock) {
            try {
                if (st.session != null && st.session.isOpen()) {
                    log.info("[KIS] 세션 종료 중: {}", st.session.getId());
                    st.session.close();
                }
            } catch (Exception e) {
                log.warn("[KIS] close error", e);
            } finally {
                st.session = null;
                st.handler = null;
                st.subscribed.clear();
            }
        }
    }

    private static class ConnectionState {
        final Object lock = new Object();
        volatile WebSocketSession session;
        volatile KisRealtimeWebSocketHandler handler;
        final Set<String> subscribed = ConcurrentHashMap.newKeySet();
    }
}