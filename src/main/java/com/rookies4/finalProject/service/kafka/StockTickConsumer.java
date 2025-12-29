package com.rookies4.finalProject.service.kafka;

import com.rookies4.finalProject.dto.kafka.StockTickDTO; // [중요] 이 경로가 정확해야 합니다.
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTickConsumer {

    private final PriceAlertService priceAlertService;
    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(
            topics = "stock-ticks",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stockTickKafkaListenerContainerFactory"
    )
    public void consumeStockTick(StockTickDTO stockTick) {
        try {
            // 1. 로그 및 데이터 유효성 검증
            if (stockTick.getStockCode() == null || stockTick.getCurrentPrice() == null) {
                return;
            }

            // 2. WebSocket 브로드캐스팅 (/topic/realtime-price/{종목코드})
            // 프론트엔드 구독 경로: /topic/realtime-price/005930
            String destination = "/topic/realtime-price/" + stockTick.getStockCode();
            messagingTemplate.convertAndSend(destination, stockTick);

            // 3. 목표가 알림 서비스 로직 수행
            priceAlertService.evaluatePriceAlerts(
                    stockTick.getStockCode(),
                    stockTick.getCurrentPrice()
            );

        } catch (Exception e) {
            log.error("Error processing stock tick: {}", e.getMessage(), e);
        }
    }
}