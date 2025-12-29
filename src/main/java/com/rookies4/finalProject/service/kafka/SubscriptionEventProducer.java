package com.rookies4.finalProject.service.kafka;

import com.rookies4.finalProject.dto.kafka.SubscriptionEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * subscription-events 토픽으로 종목 구독/해지 이벤트를 발행하는 Producer 서비스
 * 
 * 사용자가 종목을 구독하거나 해지할 때 이 서비스를 통해 이벤트를 발행합니다.
 * StockCollector가 이 이벤트를 구독하여 실시간 데이터 수집 대상을 동적으로 조절합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionEventProducer {

    private static final String TOPIC_NAME = "subscription-events";
    
    private final KafkaTemplate<String, SubscriptionEventDTO> kafkaTemplate;

    /**
     * 종목 구독 이벤트를 발행합니다.
     * 
     * @param userId 사용자 ID
     * @param stockCode 종목 코드
     */
    public void publishSubscribeEvent(Long userId, String stockCode) {
        SubscriptionEventDTO event = SubscriptionEventDTO.builder()
                .eventType(SubscriptionEventDTO.EventType.SUBSCRIBE)
                .userId(userId)
                .stockCode(stockCode)
                .timestamp(LocalDateTime.now())
                .build();
        
        publishEvent(event);
    }

    /**
     * 종목 구독 해지 이벤트를 발행합니다.
     * 
     * @param userId 사용자 ID
     * @param stockCode 종목 코드
     */
    public void publishUnsubscribeEvent(Long userId, String stockCode) {
        SubscriptionEventDTO event = SubscriptionEventDTO.builder()
                .eventType(SubscriptionEventDTO.EventType.UNSUBSCRIBE)
                .userId(userId)
                .stockCode(stockCode)
                .timestamp(LocalDateTime.now())
                .build();
        
        publishEvent(event);
    }

    /**
     * 구독/해지 이벤트를 Kafka로 발행합니다.
     * 
     * @param event 구독 이벤트
     */
    private void publishEvent(SubscriptionEventDTO event) {
        try {
            // 키는 stockCode로 설정 (같은 종목은 같은 파티션으로)
            String key = event.getStockCode();
            
            CompletableFuture<SendResult<String, SubscriptionEventDTO>> future = 
                    kafkaTemplate.send(TOPIC_NAME, key, event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("구독 이벤트 발행 성공 - EventType: {}, UserId: {}, StockCode: {}", 
                            event.getEventType(), 
                            event.getUserId(), 
                            event.getStockCode());
                } else {
                    log.error("구독 이벤트 발행 실패 - EventType: {}, UserId: {}, StockCode: {}, Error: {}", 
                            event.getEventType(), 
                            event.getUserId(), 
                            event.getStockCode(),
                            ex.getMessage(), ex);
                }
            });
            
        } catch (Exception e) {
            log.error("구독 이벤트 발행 중 예외 발생 - EventType: {}, UserId: {}, StockCode: {}, Error: {}", 
                    event.getEventType(), 
                    event.getUserId(), 
                    event.getStockCode(),
                    e.getMessage(), e);
        }
    }
}
