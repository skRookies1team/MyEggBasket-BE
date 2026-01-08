package com.rookies4.finalProject.service.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
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

    @KafkaListener(
            topics = "stock-ticks",
            groupId = "be-stock-ticks",
            containerFactory = "stockTickKafkaListenerContainerFactory"
    )
    public void consumeStockData(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String type = asString(data.get("type"));

            if (type == null) {
                // type이 없으면 메시지 내용을 보고 추론하거나 로그 남기기
                log.debug("[Kafka] type missing: {}", data);
                return;
            }

            switch (type) {
                case "STOCK_TICK" -> handleStockTick(data);
                case "ORDER_BOOK" -> handleOrderBook(data);
                default -> log.debug("[Kafka] ignored type={}", type);
            }
        } catch (Exception e) {
            log.error("[Kafka] 메시지 파싱 실패: {}", message, e);
        }
    }

    private void handleStockTick(Map<String, Object> data) {
        // [디버깅용] 실제 들어오는 키 확인을 위해 로그 레벨을 info로 유지하거나 더 자세히 출력
        log.info("[Kafka][STOCK_TICK] raw={}", data);

        try {
            StockTickDTO tick = toStockTickDTO(data);
            String stockCode = tick.getStockCode();

            if (stockCode == null) {
                log.warn("[Kafka][STOCK_TICK] stockCode missing (parsed null). Raw data: {}", data);
                return;
            }

            sendRealtimePrice(stockCode, tick);
            evaluatePriceTargetAndNotify(stockCode, tick.getCurrentPrice());

        } catch (Exception e) {
            log.error("[Kafka][STOCK_TICK] 처리 실패", e);
        }
    }

    private void handleOrderBook(Map<String, Object> data) {
        log.info("[Kafka][ORDER_BOOK] raw={}", data);

        try {
            OrderBookDTO orderBook = toOrderBookDTO(data);
            String stockCode = orderBook.getStockCode();

            if (stockCode == null) {
                log.warn("[Kafka][ORDER_BOOK] stockCode missing. Raw data: {}", data);
                return;
            }
            sendOrderBook(stockCode, orderBook);

        } catch (Exception e) {
            log.error("[Kafka][ORDER_BOOK] 처리 실패", e);
        }
    }

    // ------------------------------------------------------------------
    // DTO 변환 로직 (한글/영어/약어 키 모두 대응하도록 수정)
    // ------------------------------------------------------------------
    private StockTickDTO toStockTickDTO(Map<String, Object> data) {
        return StockTickDTO.builder()
                .type("STOCK_TICK")
                .stockCode(findString(data, "stockCode", "code", "mkscShrnIscd"))
                .tickTime(findString(data, "tickTime", "time", "stckCntgHour")) // 영어 키 우선 확인

                // 가격 관련 필드 (영어 이름 or KIS 약어)
                .currentPrice(findBigDecimal(data, "currentPrice", "price", "stckPrpr"))
                .diff(findBigDecimal(data, "diff", "changeAmount", "prdyVrss"))
                .diffRate(findBigDecimal(data, "diffRate", "changeRate", "prdyCtrt"))

                // 거래량 관련
                .volume(findBigDecimal(data, "volume", "acmlVol"))
                .tradingValue(findBigDecimal(data, "tradingValue", "acmlTrPbmn"))
                .build();
    }

    private OrderBookDTO toOrderBookDTO(Map<String, Object> data) {
        // asks, bids가 "ask", "bid" 단수형일 수도 있고 복수형일 수도 있음
        List<OrderBookDTO.OrderItem> asks = buildOrderItems(data, "asks", "ask");
        List<OrderBookDTO.OrderItem> bids = buildOrderItems(data, "bids", "bid");

        return OrderBookDTO.builder()
                .type("ORDER_BOOK")
                .stockCode(findString(data, "stockCode", "code"))
                .asks(asks)
                .bids(bids)
                .totalAskQty(findLong(data, "totalAskQty", "total_ask_qty"))
                .totalBidQty(findLong(data, "totalBidQty", "total_bid_qty"))
                .build();
    }

    // ------------------------------------------------------------------
    // 유틸리티 메소드 (여러 키 중 하나라도 있으면 반환)
    // ------------------------------------------------------------------

    private String findString(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            if (data.containsKey(key) && data.get(key) != null) {
                return String.valueOf(data.get(key));
            }
        }
        return null;
    }

    private BigDecimal findBigDecimal(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object val = data.get(key);
            if (val != null) {
                try {
                    return new BigDecimal(String.valueOf(val));
                } catch (NumberFormatException ignored) {}
            }
        }
        return null; // 못 찾으면 null 반환
    }

    private Long findLong(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object val = data.get(key);
            if (val != null) {
                try {
                    return Long.parseLong(String.valueOf(val));
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    // 호가 리스트 추출 (복수 키 지원)
    private List<OrderBookDTO.OrderItem> buildOrderItems(Map<String, Object> data, String... keys) {
        Object rawList = null;
        for (String key : keys) {
            if (data.containsKey(key)) {
                rawList = data.get(key);
                break;
            }
        }

        if (rawList == null) {
            return List.of();
        }

        List<Map<String, Object>> items = objectMapper.convertValue(
                rawList, new TypeReference<List<Map<String, Object>>>() {}
        );

        if (items == null) return List.of();

        return items.stream()
                .map(item -> new OrderBookDTO.OrderItem(
                        findBigDecimal(item, "price", "p", "askp", "bidp"), // 호가 가격 키 후보
                        findLong(item, "qty", "quantity", "volume", "v", "askpRsqn", "bidpRsqn") // 호가 잔량 키 후보
                ))
                .toList();
    }

    private void sendRealtimePrice(String stockCode, StockTickDTO tick) {
        messagingTemplate.convertAndSend("/topic/realtime-price/" + stockCode, tick);
    }

    private void sendOrderBook(String stockCode, OrderBookDTO orderBook) {
        messagingTemplate.convertAndSend("/topic/stock-order-book/" + stockCode, orderBook);
    }

    // 목표가 알림 로직 (기존 유지)
    private void evaluatePriceTargetAndNotify(String stockCode, BigDecimal currentPrice) {
        if (currentPrice == null) return;
        List<PriceTarget> reachedTargets = priceTargetService.evaluate(stockCode, currentPrice);
        for (PriceTarget target : reachedTargets) {
            if (target.getUpperTriggered() == Boolean.TRUE && target.getUpperTriggeredAt() != null) {
                sendPriceAlert(buildAlertPayload(target, currentPrice, AlertType.UPPER));
            }
            if (target.getLowerTriggered() == Boolean.TRUE && target.getLowerTriggeredAt() != null) {
                sendPriceAlert(buildAlertPayload(target, currentPrice, AlertType.LOWER));
            }
        }
    }

    private void sendPriceAlert(PriceAlertMessageDTO payload) {
        messagingTemplate.convertAndSend("/topic/price-alert/" + payload.getUserId(), payload);
    }

    private PriceAlertMessageDTO buildAlertPayload(PriceTarget target, BigDecimal currentPrice, AlertType alertType) {
        return PriceAlertMessageDTO.builder()
                .alertId(target.getUser().getId() + "-" + target.getStock().getStockCode() + "-" + alertType.name() + "-" + System.currentTimeMillis())
                .userId(target.getUser().getId())
                .stockCode(target.getStock().getStockCode())
                .stockName(target.getStock().getName())
                .alertType(alertType)
                .targetPrice(alertType == AlertType.UPPER ? target.getUpperTarget() : target.getLowerTarget())
                .currentPrice(currentPrice)
                .triggeredAt(alertType == AlertType.UPPER ? target.getUpperTriggeredAt() : target.getLowerTriggeredAt())
                .build();
    }

    private String asString(Object v) { return v == null ? null : String.valueOf(v); }
}