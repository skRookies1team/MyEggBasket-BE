package com.rookies4.finalProject.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.domain.entity.PriceTarget;
import com.rookies4.finalProject.dto.kafka.OrderBookDTO;
import com.rookies4.finalProject.dto.kafka.PriceAlertMessageDTO;
import com.rookies4.finalProject.dto.kafka.PriceAlertMessageDTO.AlertType;
import com.rookies4.finalProject.dto.kafka.StockTickDTO;
import com.rookies4.finalProject.service.PriceTargetService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTickConsumer {

    private final PriceTargetService priceTargetService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    // Kafka stock-ticks 토픽 리스닝
    @KafkaListener(
            topics = "stock-ticks",
            groupId = "be-stock-ticks",
            containerFactory = "stockTickKafkaListenerContainerFactory"
    )
    public void consumeStockData(String message) {
        try {
            Map<String, Object> data =
                    objectMapper.readValue(message, Map.class);

            String type = asString(data.get("type"));
            if (type == null) {
                log.debug("[Kafka] type missing: {}", data);
                return;
            }

            switch (type) {
                case "STOCK_TICK" -> handleStockTick(data); // 실시간 체결가
                case "ORDER_BOOK" -> handleOrderBook(data); // 실시간 호가
                default -> log.debug("[Kafka] ignored type={}", type);
            }
        } catch (Exception e) {
            log.error("[Kafka] 메시지 파싱 실패: {}", message, e);
        }
    }

    // 실시간 체결가(STOCK_TICK) 처리
    private void handleStockTick(Map<String, Object> data) {
        log.info("[Kafka][STOCK_TICK] raw={}", data);

        try {
            StockTickDTO tick = toStockTickDTO(data);
            String stockCode = tick.getStockCode();

            if (stockCode == null) {
                log.warn("[Kafka][STOCK_TICK] stockCode missing: {}", data);
                return;
            }

            sendRealtimePrice(stockCode, tick);
            evaluatePriceTargetAndNotify(stockCode, tick.getCurrentPrice());

        } catch (Exception e) {
            log.error("[Kafka][STOCK_TICK] 처리 실패: {}", e.getMessage(), e);
        }
    }

    // 실시간 호가(ORDER_BOOK) 처리
    private void handleOrderBook(Map<String, Object> data) {
        log.info("[Kafka][ORDER_BOOK] raw={}", data);

        try {
            OrderBookDTO orderBook = toOrderBookDTO(data);
            String stockCode = orderBook.getStockCode();

            if (stockCode == null) {
                log.warn("[Kafka][ORDER_BOOK] stockCode missing: {}", data);
                return;
            }

            sendOrderBook(stockCode, orderBook);

        } catch (Exception e) {
            log.error("[Kafka][ORDER_BOOK] 처리 실패: {}", e.getMessage(), e);
        }
    }

    // kafka 메시지 DTO로 변환 - StockTick
    private StockTickDTO toStockTickDTO(Map<String, Object> data) {
        return StockTickDTO.builder()
                .type("STOCK_TICK")
                .stockCode(asString(data.get("stockCode")))
                .tickTime(asString(data.get("stckCntgHour")))

                .currentPrice(getBigDecimal(data, "stckPrpr"))
                .diff(getBigDecimal(data, "prdyVrss"))
                .diffRate(getBigDecimal(data, "prdyCtrt"))

                .volume(getBigDecimal(data, "acmlVol"))
                .tradingValue(getBigDecimal(data, "acmlTrPbmn"))
                .build();
    }

    // kafka 메시지 DTO로 변환 - OrderBook
    private OrderBookDTO toOrderBookDTO(Map<String, Object> data) {

        List<OrderBookDTO.OrderItem> asks = buildOrderItems(data, "ask");
        List<OrderBookDTO.OrderItem> bids = buildOrderItems(data, "bid");

        return OrderBookDTO.builder()
                .type("ORDER_BOOK")
                .stockCode(asString(data.get("stockCode")))
                .asks(asks)
                .bids(bids)
                .totalAskQty(getLong(data, "totalAskQty"))
                .totalBidQty(getLong(data, "totalBidQty"))
                .build();
    }

    // 실시간 체결가 프론트로 전송
    private void sendRealtimePrice(String stockCode, StockTickDTO tick) {
        messagingTemplate.convertAndSend(
                "/topic/realtime-price/" + stockCode,
                tick
        );
    }

    // 실시간 호가 프론트로 전송
    private void sendOrderBook(String stockCode, OrderBookDTO orderBook) {
        messagingTemplate.convertAndSend(
                "/topic/stock-order-book/" + stockCode,
                orderBook
        );
    }

    private void evaluatePriceTargetAndNotify(String stockCode, BigDecimal currentPrice) {
        if (currentPrice == null) {
            return;
        }

        // 목표가 도달한 PriceTarget 목록 반환
        List<PriceTarget> reachedTargets =
                priceTargetService.evaluate(stockCode, currentPrice);

        for (PriceTarget target : reachedTargets) {

            // 상한 목표가 알림
            if (target.getUpperTriggered() == Boolean.TRUE
                    && target.getUpperTriggeredAt() != null) {

                PriceAlertMessageDTO payload = buildAlertPayload(target, currentPrice, AlertType.UPPER);

                sendPriceAlert(payload);
            }

            // 하한 목표가 알림
            if (target.getLowerTriggered() == Boolean.TRUE
                    && target.getLowerTriggeredAt() != null) {

                PriceAlertMessageDTO payload = buildAlertPayload(target, currentPrice, AlertType.LOWER);

                sendPriceAlert(payload);
            }
        }
    }

    // 목표가 도달 알람을 프론트로 전송
    private void sendPriceAlert (PriceAlertMessageDTO payload){
        Long userId = payload.getUserId();

        messagingTemplate.convertAndSend(
                "/topic/price-alert/" + userId,
                payload
        );
    }

    // 프론트로 보낼 목표가 알람 payload 생성
    private PriceAlertMessageDTO buildAlertPayload (PriceTarget target, BigDecimal
    currentPrice, PriceAlertMessageDTO.AlertType alertType){
        return PriceAlertMessageDTO.builder()
                .alertId(generateAlertId(target, alertType))
                .userId(target.getUser().getId())
                .stockCode(target.getStock().getStockCode())
                .stockName(target.getStock().getName())
                .alertType(alertType)
                .targetPrice(
                        alertType == AlertType.UPPER
                                ? target.getUpperTarget()
                                : target.getLowerTarget()
                )
                .currentPrice(currentPrice)
                .triggeredAt(
                        alertType == AlertType.UPPER
                                ? target.getUpperTriggeredAt()
                                : target.getLowerTriggeredAt()
                )
                .build();
    }

    private List<OrderBookDTO.OrderItem> buildOrderItems (Map < String, Object > data, String key){
        List<Map<String, Object>> rawItems =
                (List<Map<String, Object>>) data.get(key);

        if (rawItems == null) {
            return List.of();
        }

        return rawItems.stream()
                .map(item -> new OrderBookDTO.OrderItem(
                        getBigDecimal(item, "price"),
                        getLong(item, "qty")
                ))
                .toList();
    }

    private String generateAlertId (PriceTarget target, AlertType alertType){
        return target.getUser().getId()
                + "-" + target.getStock().getStockCode()
                + "-" + alertType.name()
                + "-" + System.currentTimeMillis();
    }

    // Object → String 안전 변환
    private String asString (Object v){
        return v == null ? null : String.valueOf(v);
    }

    private BigDecimal getBigDecimal (Map < String, Object > data, String key){
        Object v = data.get(key);
        return v == null ? null : new BigDecimal(v.toString());
    }

    private Long getLong (Map < String, Object > data, String key){
        Object v = data.get(key);
        return v == null ? null : Long.parseLong(v.toString());
    }
}