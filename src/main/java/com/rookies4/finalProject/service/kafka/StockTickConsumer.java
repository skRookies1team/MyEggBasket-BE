package com.rookies4.finalProject.service.kafka;

import com.rookies4.finalProject.dto.kafka.StockTickDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * stock-ticks 토픽을 구독하는 Kafka Consumer 서비스
 * 
 * StockCollector가 수집한 실시간 체결 데이터를 수신합니다.
 * 
 * 처리 흐름:
 * 1. stock-ticks 토픽에서 메시지 수신
 * 2. 종목 코드, 현재가 추출
 * 3. (향후) 해당 종목을 구독 중인 사용자 목록 조회 (StockSubscription 테이블)
 * 4. (향후) 사용자별 목표가 조건 평가
 * 5. (향후) 조건 만족 시 → price-alert-events 토픽으로 이벤트 발행
 * 
 * 참고: 현재는 목표가 기능이 구현되지 않아 로그만 출력합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockTickConsumer {

    private final PriceAlertService priceAlertService;

    /**
     * stock-ticks 토픽에서 실시간 체결 데이터를 수신합니다.
     * 
     * Consumer는 얇게 유지: 역직렬화 → 서비스 호출만 담당
     * 
     * @param stockTick 실시간 체결 데이터
     */
    @KafkaListener(
            topics = "stock-ticks",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "stockTickKafkaListenerContainerFactory"
    )
    public void consumeStockTick(StockTickDTO stockTick) {
        try {
            log.debug("Received stock tick - StockCode: {}, CurrentPrice: {}, Timestamp: {}", 
                    stockTick.getStockCode(), 
                    stockTick.getCurrentPrice(), 
                    stockTick.getTimestamp());
            
            // 필수 데이터 검증
            if (stockTick.getStockCode() == null || stockTick.getCurrentPrice() == null) {
                log.warn("Invalid stock tick received - missing required fields");
                return;
            }
            
            // 목표가 조건 평가 (비즈니스 로직은 서비스에서 처리)
            priceAlertService.evaluatePriceAlerts(
                    stockTick.getStockCode(), 
                    stockTick.getCurrentPrice()
            );
            
        } catch (Exception e) {
            log.error("Error processing stock tick - StockCode: {}, Error: {}", 
                    stockTick != null ? stockTick.getStockCode() : "unknown", 
                    e.getMessage(), e);
            // Consumer는 예외를 삼키고 계속 처리 (Dead Letter Queue 설정도 고려 가능)
        }
    }
}
