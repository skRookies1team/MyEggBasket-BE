package com.rookies4.finalProject.service.kafka;

import com.rookies4.finalProject.dto.kafka.SubscriptionEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionEventProducer {

    private final KafkaTemplate<String, SubscriptionEventDTO> kafkaTemplate;
    private static final String TOPIC = "subscription-events";

    /**
     * [추가됨] 완성된 DTO를 받아서 Kafka로 전송하는 메서드
     */
    public void sendSubscriptionEvent(SubscriptionEventDTO event) {
        publishEvent(event);
    }

    public void publishSubscribeEvent(Long userId, String stockCode) {
        SubscriptionEventDTO event = SubscriptionEventDTO.builder()
                .eventType(SubscriptionEventDTO.EventType.SUBSCRIBE)
                .userId(userId)
                .stockCode(stockCode)
                .timestamp(LocalDateTime.now())
                .build();
        publishEvent(event);
    }

    public void publishUnsubscribeEvent(Long userId, String stockCode) {
        SubscriptionEventDTO event = SubscriptionEventDTO.builder()
                .eventType(SubscriptionEventDTO.EventType.UNSUBSCRIBE)
                .userId(userId)
                .stockCode(stockCode)
                .timestamp(LocalDateTime.now())
                .build();
        publishEvent(event);
    }

    private void publishEvent(SubscriptionEventDTO event) {
        CompletableFuture<SendResult<String, SubscriptionEventDTO>> future =
                kafkaTemplate.send(TOPIC, event.getStockCode(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Sent subscription event: [Type: {}, SubType: {}, Stock: {}]",
                        event.getEventType(), event.getSubType(), event.getStockCode());
            } else {
                log.error("Failed to send subscription event: {}", ex.getMessage());
            }
        });
    }
}