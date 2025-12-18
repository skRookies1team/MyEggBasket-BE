package com.rookies4.finalProject.websocket;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.service.KisAuthService;
import com.rookies4.finalProject.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisRealtimeConnector {

    private static final String REAL_WS_URL = "ws://ops.koreainvestment.com:21000";
    private static final String VIRTUAL_WS_URL = "ws://ops.koreainvestment.com:31000";
    private static final String CUST_TYPE = "P";
    private static final Long FALLBACK_USER_ID = 1L;

    private final KisAuthService kisAuthService;
    private final UserService userService;
    private final RealtimePriceBroadcaster broadcaster;

    private final ConnectionState real = new ConnectionState();
    private final ConnectionState virtual = new ConnectionState();

    public void connectIfAbsent(boolean useVirtualServer, String stockCode) {
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

    public void disconnectIfPresent(boolean useVirtualServer, String stockCode) {
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