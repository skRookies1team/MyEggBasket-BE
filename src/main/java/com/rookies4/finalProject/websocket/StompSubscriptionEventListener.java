package com.rookies4.finalProject.websocket;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompSubscriptionEventListener {

    private static final String DEST_PREFIX = "/topic/realtime-price/";

    private final RealtimeSubscriptionManager subscriptionManager;
    private final KisRealtimeConnector kisConnector;

    // ✅ sessionId -> (subscriptionId -> useVirtualServer)
    private final Map<String, Map<String, Boolean>> virtualBySub = new ConcurrentHashMap<>();

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = acc.getSessionId();
        String subscriptionId = acc.getSubscriptionId();
        String destination = acc.getDestination();

        if (sessionId == null || subscriptionId == null || destination == null) return;
        if (!destination.startsWith(DEST_PREFIX)) return;

        int cur = subscriptionManager.getSessionSubscriptionCount(sessionId);
        if (cur >= 50) {
            log.warn("[STOMP] over limit(50). session={}", sessionId);
            return;
        }

        boolean useVirtualServer = readVirtualFlag(acc);

        // ✅ virtual 값 저장(나중에 unsubscribe/disconnect에서 필요)
        virtualBySub
                .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(subscriptionId, useVirtualServer);

        int count = subscriptionManager.addSubscribe(sessionId, subscriptionId, destination);

        String stockCode = destination.substring(DEST_PREFIX.length());
        log.info("[STOMP] subscribe session={}, stockCode={}, subscribers={}, virtual={}",
                sessionId, stockCode, count, useVirtualServer);

        if (count == 1) {
            kisConnector.connectIfAbsent(useVirtualServer, stockCode);
        }
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = acc.getSessionId();
        String subscriptionId = acc.getSubscriptionId();
        if (sessionId == null || subscriptionId == null) return;

        RealtimeSubscriptionManager.UnsubResult r =
                subscriptionManager.removeUnsubscribe(sessionId, subscriptionId);

        // ✅ virtual 조회 후 제거
        boolean useVirtualServer = removeVirtualFlag(sessionId, subscriptionId);

        if (r.destination() == null) return;
        if (!r.destination().startsWith(DEST_PREFIX)) return;

        String stockCode = r.destination().substring(DEST_PREFIX.length());

        log.info("[STOMP] unsubscribe session={}, stockCode={}, remain={}, virtual={}",
                sessionId, stockCode, r.remain(), useVirtualServer);

        if (r.remain() == 0) {
            kisConnector.disconnectIfPresent(useVirtualServer, stockCode); // ✅ 시그니처 맞춤
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null) return;

        // disconnect인 경우: 이 세션이 마지막 구독자였던 destination들만 내려옴
        Set<String> zeroDestinations = subscriptionManager.removeAllByDisconnect(sessionId);

        // ✅ 세션의 subscriptionId -> virtual 맵 제거 (기본은 false로 처리)
        Map<String, Boolean> subsVirtual = virtualBySub.remove(sessionId);

        for (String dest : zeroDestinations) {
            if (!dest.startsWith(DEST_PREFIX)) continue;

            String stockCode = dest.substring(DEST_PREFIX.length());

            // 세션 disconnect 상황에선 “어떤 subscriptionId였는지”를 destination만으론 모르니
            // virtual을 정확히 매핑하기 어려움 → 테스트/기본은 false 처리
            boolean useVirtualServer = false;
            if (subsVirtual != null && subsVirtual.containsValue(true)) {
                // 세션에서 virtual=true로 구독한 게 하나라도 있으면 true로 보는 보수적 처리
                useVirtualServer = true;
            }

            log.info("[STOMP] disconnect session={}, last-subscriber stockCode={}, virtual={}",
                    sessionId, stockCode, useVirtualServer);

            kisConnector.disconnectIfPresent(useVirtualServer, stockCode); // ✅ 시그니처 맞춤
        }
    }

    private boolean readVirtualFlag(StompHeaderAccessor acc) {
        List<String> v1 = acc.getNativeHeader("virtual");
        if (v1 != null && !v1.isEmpty()) return Boolean.parseBoolean(v1.get(0));

        List<String> v2 = acc.getNativeHeader("useVirtualServer");
        if (v2 != null && !v2.isEmpty()) return Boolean.parseBoolean(v2.get(0));

        return false;
    }

    private boolean removeVirtualFlag(String sessionId, String subscriptionId) {
        Map<String, Boolean> m = virtualBySub.get(sessionId);
        if (m == null) return false;

        Boolean v = m.remove(subscriptionId);
        if (m.isEmpty()) virtualBySub.remove(sessionId);

        return v != null && v;
    }
}