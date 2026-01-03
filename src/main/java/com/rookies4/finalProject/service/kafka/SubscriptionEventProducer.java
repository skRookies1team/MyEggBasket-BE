package com.rookies4.finalProject.service.kafka;

import com.rookies4.finalProject.dto.kafka.SubscriptionEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * subscription-events 토픽으로 구독/해지 이벤트를 발행하는 Producer 서비스
 * 
 * 사용자가 종목을 구독하거나 해지할 때 이벤트를 발행하여
 * 실시간 데이터 수집기(Collector)가 해당 종목을 추적하도록 트리거합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionEventProducer {

    private final KafkaTemplate<String, SubscriptionEventDTO> subscriptionEventKafkaTemplate;

    private static final String TOPIC = "subscription-events";

    public void sendSubscriptionEvent(SubscriptionEventDTO event) {
        publishEvent(event);
    }

    public void publishSubscribeEvent(Long userId, String stockCode) {
        log.info(" [Backend] Publishing SUBSCRIBE event for: {}", stockCode); // [디버깅 로그]
        SubscriptionEventDTO event = SubscriptionEventDTO.builder()
                .eventType(SubscriptionEventDTO.EventType.SUBSCRIBE)
                .subType("VIEW") // 기본값 명시
                .userId(userId)
                .stockCode(stockCode)
                .timestamp(LocalDateTime.now())
                .build();
        publishEvent(event);
    }

    public void publishUnsubscribeEvent(Long userId, String stockCode) {
        log.info(" [Backend] Publishing UNSUBSCRIBE event for: {}", stockCode); // [디버깅 로그]
        SubscriptionEventDTO event = SubscriptionEventDTO.builder()
                .eventType(SubscriptionEventDTO.EventType.UNSUBSCRIBE)
                .subType("VIEW")
                .userId(userId)
                .stockCode(stockCode)
                .timestamp(LocalDateTime.now())
                .build();
        publishEvent(event);
    }

    private void publishEvent(SubscriptionEventDTO event) {
        try {
            log.info("[Kafka] Sending message to topic '{}': {}", TOPIC, event);

            // [수정] 동기식 전송으로 변경 (최대 3초 대기)
            // 에러가 있다면 여기서 즉시 Exception이 터져서 로그에 찍힘
            SendResult<String, SubscriptionEventDTO> result = subscriptionEventKafkaTemplate
                    .send(TOPIC, event.getStockCode(), event)
                    .get(3, TimeUnit.SECONDS);

            log.info("[Kafka] Sent successfully! Offset: {}", result.getRecordMetadata().offset());

        } catch (Exception e) {
            log.error("[Kafka] Failed to send message: {}", e.getMessage());
            e.printStackTrace(); // 콘솔에 에러 스택 트레이스 출력
            throw new RuntimeException("Kafka 전송 실패: " + e.getMessage());
        }
    }
}