package com.rookies4.finalProject.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.dto.kafka.StockTickDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTickConsumer {

    private final PriceAlertService priceAlertService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper; // 데이터 변환용

    @KafkaListener(
            topics = "stock-ticks",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stockTickKafkaListenerContainerFactory"
    )
    public void consumeStockData(Map<String, Object> data) {
        try {
            String type = (String) data.get("type");
            String stockCode = (String) data.get("stockCode");

            if (stockCode == null) return;

            if ("STOCK_TICK".equals(type)) {
                // 1. 체결가 처리
                StockTickDTO stockTick = objectMapper.convertValue(data, StockTickDTO.class);

                // WS 전송
                messagingTemplate.convertAndSend("/topic/realtime-price/" + stockCode, stockTick);

                // 알림 체크
                priceAlertService.evaluatePriceAlerts(stockCode, stockTick.getCurrentPrice());

            } else if ("ORDER_BOOK".equals(type)) {
                // 2. 호가 처리
                // Map을 DTO로 변환하거나, 그대로 전송해도 무방합니다. 여기서는 Map 그대로 전송 예시
                // 프론트 구독 경로: /topic/stock-order-book/005930
                messagingTemplate.convertAndSend("/topic/stock-order-book/" + stockCode, data);
            }

        } catch (Exception e) {
            log.error("Error processing stock data: {}", e.getMessage());
        }
    }
}