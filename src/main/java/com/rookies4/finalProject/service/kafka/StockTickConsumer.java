package com.rookies4.finalProject.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.domain.entity.PriceTarget;
import com.rookies4.finalProject.dto.kafka.StockTickDTO;
import com.rookies4.finalProject.service.PriceTargetService;
import java.time.LocalDateTime;
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
    public void consumeStockData(Map<String, Object> data) {
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
    }

    // 실시간 체결가(STOCK_TICK) 처리
    private void handleStockTick(Map<String, Object> data) {
        try {
            StockTickDTO tick = objectMapper.convertValue(data, StockTickDTO.class);
            String stockCode = resolveStockCode(tick, data);

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
        String stockCode = resolveStockCode(null, data);
        if (stockCode == null) {
            log.warn("[Kafka][ORDER_BOOK] stockCode missing: {}", data);
            return;
        }

        messagingTemplate.convertAndSend(
                "/topic/order-book/" + stockCode,
                data
        );
    }

    // 실시간 체결가 프론트로 전송
    private void sendRealtimePrice(String stockCode, StockTickDTO tick) {
        messagingTemplate.convertAndSend(
                "/topic/realtime-price/" + stockCode,
                tick
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
            sendPriceAlert(target, currentPrice);
        }
    }

    // 목표가 도달 알람을 프론트로 전송
    private void sendPriceAlert(PriceTarget target, BigDecimal currentPrice) {
        Long userId = target.getUser().getId();

        messagingTemplate.convertAndSend(
                "/topic/price-alert/" + userId,
                buildAlertPayload(target, currentPrice)
        );
    }

    // 프론트로 보낼 목표가 알람 payload 생성
    private Map<String, Object> buildAlertPayload(PriceTarget target, BigDecimal currentPrice) {
        return Map.of(
                "userId", target.getUser().getId(),
                "stockCode", target.getStock().getStockCode(),
                "currentPrice", currentPrice,
                "upperTarget", target.getUpperTarget(),
                "lowerTarget", target.getLowerTarget(),
                "triggeredAt", LocalDateTime.now()
        );
    }

    /**
     * 종목 코드 추출
     * - DTO 우선
     * - 없으면 Kafka raw 데이터에서 fallback
     */
    private String resolveStockCode(StockTickDTO tick, Map<String, Object> data) {
        if (tick != null && tick.getStockCode() != null) {
            return tick.getStockCode();
        }
        return asString(data.get("stockCode")) != null
                ? asString(data.get("stockCode"))
                : asString(data.get("stckShrnIscd"));
    }

    // Object → String 안전 변환
    private String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}