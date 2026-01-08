package com.rookies4.finalProject.websocket;

import com.rookies4.finalProject.service.kafka.SubscriptionEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 변경된 역할:
 * 이전에는 직접 KIS WebSocket에 연결했지만,
 * 이제는 Kafka(subscription-events)로 구독 명령을 발행하는 역할만 수행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisRealtimeConnector {

    private final SubscriptionEventProducer subscriptionEventProducer;

    // 시스템용 ID (수집기에게 '시스템이 요청함'을 알리기 위함)
    private static final Long SYSTEM_USER_ID = 0L;

    /**
     * 프론트엔드에서 구독 요청이 들어왔을 때 호출됨 (최초 1명일 때)
     * -> Kafka로 SUBSCRIBE 이벤트 발송
     */
    public void connectIfAbsent(boolean useVirtualServer, String stockCode) {
        log.info("[Kafka] Requesting SUBSCRIBE for stock: {} (Virtual: {})", stockCode, useVirtualServer);
        
        // Python 수집기가 알아들을 수 있도록 구독 이벤트 발행
        subscriptionEventProducer.publishSubscribeEvent(SYSTEM_USER_ID, stockCode);
    }

    /**
     * 프론트엔드 구독자가 0명이 되었을 때 호출됨
     * -> Kafka로 UNSUBSCRIBE 이벤트 발송
     */
    public void scheduleDisconnect(boolean useVirtualServer, String stockCode) {
        log.info("[Kafka] Requesting UNSUBSCRIBE for stock: {} (Virtual: {})", stockCode, useVirtualServer);
        
        // Python 수집기에게 수집 중단 요청
        subscriptionEventProducer.publishUnsubscribeEvent(SYSTEM_USER_ID, stockCode);
    }
}