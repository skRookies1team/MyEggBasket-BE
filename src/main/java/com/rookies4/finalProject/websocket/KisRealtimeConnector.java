package com.rookies4.finalProject.websocket;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.service.KisAuthService;
import com.rookies4.finalProject.service.UserService;
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

    /**
     * 페이지 이동/리렌더링 등으로 STOMP 세션이 잠깐 끊겼다가 재연결되는 경우가 있습니다.
     * 그 순간에 "마지막 구독자=0" 이벤트가 들어오면, 우리가 즉시 KIS UNREGISTER/close를 해버려서
     * 메인페이지(또는 공통 리스트)의 실시간 체결가가 끊겨 보일 수 있습니다.
     *
     * 그래서 "마지막 구독자=0"이 되더라도 바로 끊지 않고, 잠깐의 유예 시간(grace)을 둔 뒤 끊습니다.
     * 유예 시간 내에 다시 구독이 들어오면(=사용자 페이지 전환/재연결), 예약된 disconnect는 취소됩니다.
     */
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
     * 유예 시간 내에 다시 구독이 오면 connectIfAbsent()에서 자동 cancel됩니다.
     */
    public void scheduleDisconnect(boolean useVirtualServer, String stockCode) {
        Map<String, ScheduledFuture<?>> taskMap = useVirtualServer ? virtualDisconnectTasks : realDisconnectTasks;

        // 이미 예약이 있으면 갱신하지 않음(중복 예약 방지)
        if (taskMap.containsKey(stockCode)) return;

        ScheduledFuture<?> future = disconnectScheduler.schedule(() -> {
            try {
                // 실행 시점에 "이 예약이 아직 유효한지" 확인
                ScheduledFuture<?> cur = taskMap.get(stockCode);
                if (cur == null) return;

                // 현재 예약을 제거하고, 실제 disconnect 수행
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
                if (st.session != null && st.session.isOpen()) st.session.close();
            } catch (Exception e) {
                log.warn("[KIS] close error", e);
            } finally {
                st.session = null;
                st.handler = null;
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