package com.rookies4.finalProject.service.kafka;

import com.rookies4.finalProject.dto.kafka.PriceAlertEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * price-alert-events 토픽으로 목표가 알림 이벤트를 발행하는 Producer 서비스
 * 
 * 목표가 조건이 만족되었을 때 이 서비스를 통해 이벤트를 발행합니다.
 * WS 전송 파드가 이 이벤트를 구독하여 클라이언트에게 실시간 알림을 전송합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertEventProducer {

    private static final String TOPIC_NAME = "price-alert-events";
    
    private final KafkaTemplate<String, PriceAlertEventDTO> kafkaTemplate;

    /**
     * 목표가 알림 이벤트를 Kafka로 발행합니다.
     * 
     * @param event 목표가 알림 이벤트
     */
    public void publishPriceAlertEvent(PriceAlertEventDTO event) {
        try {
            // 키는 userId-stockCode 조합으로 설정 (같은 사용자+종목은 같은 파티션으로)
            String key = event.getUserId() + "-" + event.getStockCode();
            
            CompletableFuture<SendResult<String, PriceAlertEventDTO>> future = 
                    kafkaTemplate.send(TOPIC_NAME, key, event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Price alert event published successfully - EventId: {}, UserId: {}, StockCode: {}, AlertType: {}, TriggerPrice: {}, CurrentPrice: {}", 
                            event.getEventId(), 
                            event.getUserId(), 
                            event.getStockCode(),
                            event.getAlertType(),
                            event.getTriggerPrice(),
                            event.getCurrentPrice());
                } else {
                    log.error("Failed to publish price alert event - EventId: {}, UserId: {}, StockCode: {}, Error: {}", 
                            event.getEventId(), 
                            event.getUserId(), 
                            event.getStockCode(),
                            ex.getMessage(), ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Exception occurred while publishing price alert event - EventId: {}, Error: {}", 
                    event.getEventId(), e.getMessage(), e);
        }
    }
}
