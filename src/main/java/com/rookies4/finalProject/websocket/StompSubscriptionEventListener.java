package com.rookies4.finalProject.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STOMP SUBSCRIBE/ UNSUBSCRIBE 이벤트 리스너
 * 역할:
 * - JWT 인증된 세션만 "유효 구독자"로 카운트
 * - 구독자 수 0 -> 1 : Kafka SUBSCRIBE 요청
 * - 구독자 수 1 -> 0 : Kafka UNSUBSCRIBE 요청
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompSubscriptionEventListener {

    private final KisRealtimeConnector kisRealtimeConnector;

    // 종목별 구독 세션 관리
    private final Map<String, Set<String>> subscribersByStock = new ConcurrentHashMap<>();

    // STOMP SUBSCRIBE 이벤트 처리
    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        // STOMP 구독 아니면 무시
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return;
        }

        // 1. 인증 여부 확인 (CONNECT 단계에서 세팅한 값)
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Boolean authenticated = (Boolean) sessionAttributes.get(StompAuthChannelInterceptor.AUTHENTICATED);

        if (!Boolean.TRUE.equals(authenticated)) {
            log.warn("[WS] 인증되지 않은 사용자가 구독을 요청했습니다. sessionId={}", accessor.getSessionId());
            return; // 인증되지 않은 세션은 카운트 X
        }

        // 2. 구독 destination 에서 stockCode 추출 (/topic/realtime-price/005930)
        String destination = accessor.getDestination();
        String stockCode = extractStockCode(destination);

        if (stockCode == null) {
            return;
        }

        // 3. 구독자 수 증가
        subscribersByStock
                .computeIfAbsent(stockCode, k -> ConcurrentHashMap.newKeySet())
                .add(accessor.getSessionId());

        int count = subscribersByStock.get(stockCode).size();
        log.info("[WS] SUBSCRIBE 종목코드={}, 구독자 수={}", stockCode, count);

        // 4. 최초 구독자일 때만 Kafka SUBSCRIBE 요청
        if (count == 1) {
            kisRealtimeConnector.connectIfAbsent(false, stockCode);
        }
    }

    // STOMP UNSUBSCRIBE 이벤트 처리
    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        removeSessionFromAllStocks(accessor.getSessionId());
    }

    // WebSocket DISCONNECT 처리 (브라우저 종료, 강제 종료 등)
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        removeSessionFromAllStocks(event.getSessionId());
    }

    // 세션이 구독 중이던 모든 종목에서 제거
    private void removeSessionFromAllStocks(String sessionId) {
        subscribersByStock.forEach((stockCode, sessions) -> {
            if (sessions.remove(sessionId)) {
                int count = sessions.size();
                log.info("[WS] UNSUBSCRIBE 종목코드={}, 구독자 수={}", stockCode, count);

                // 마지막 구독자가 나간 경우
                if (count == 0) {
                    kisRealtimeConnector.scheduleDisconnect(false, stockCode);
                }
            }
        });
    }

    // destination 에서 종목코드 추출 (/topic/realtime-price/{stockCode})
    private String extractStockCode(String destination) {
        if (destination == null) {
            return null;
        }

        String[] parts = destination.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }
}