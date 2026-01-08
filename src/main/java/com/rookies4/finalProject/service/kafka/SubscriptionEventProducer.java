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

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionEventProducer {

    // KafkaConfig에서 빈 이름이 'subscriptionEventKafkaTemplate'이므로 명시적으로 주입받는 것이 안전합니다.
    // (현재는 타입 매칭으로 잘 될 수도 있지만, 확실하게 하기 위함)
    private final KafkaTemplate<String, SubscriptionEventDTO> subscriptionEventKafkaTemplate;

    private static final String TOPIC = "subscription-events";

    public void sendSubscriptionEvent(SubscriptionEventDTO event) {
        publishEvent(event);
    }

    public void publishSubscribeEvent(Long userId, String stockCode) {
        log.info("▶️ [Backend] Publishing SUBSCRIBE event for: {}", stockCode); // [디버깅 로그]
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
        log.info("▶️ [Backend] Publishing UNSUBSCRIBE event for: {}", stockCode); // [디버깅 로그]
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
            log.info("🚀 [Kafka] Sending message to topic '{}': {}", TOPIC, event);

            // [수정] 동기식 전송으로 변경 (최대 3초 대기)
            // 에러가 있다면 여기서 즉시 Exception이 터져서 로그에 찍힘
            SendResult<String, SubscriptionEventDTO> result = subscriptionEventKafkaTemplate
                    .send(TOPIC, event.getStockCode(), event)
                    .get(3, TimeUnit.SECONDS);

            log.info("✅ [Kafka] Sent successfully! Offset: {}", result.getRecordMetadata().offset());

        } catch (Exception e) {
            log.error("❌ [Kafka] Failed to send message: {}", e.getMessage());
            e.printStackTrace(); // 콘솔에 에러 스택 트레이스 출력
            throw new RuntimeException("Kafka 전송 실패: " + e.getMessage());
        }
    }
}