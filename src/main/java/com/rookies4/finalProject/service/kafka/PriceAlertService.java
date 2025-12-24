package com.rookies4.finalProject.service.kafka;

import com.rookies4.finalProject.dto.kafka.PriceAlertEventDTO;
// TODO: 나중에 주석 해제
// import com.rookies4.finalProject.domain.entity.PriceTarget;
// import com.rookies4.finalProject.repository.PriceTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 목표가 조건을 평가하고 알림 이벤트를 생성하는 비즈니스 로직 서비스
 * 
 * 실시간 체결 데이터를 기반으로 사용자별 목표가 조건을 체크하고,
 * 조건이 만족되면 price-alert-events로 알림 이벤트를 발행합니다.
 * 
 * TODO: 현재는 사용자 목표가 조회를 위한 DB 테이블이 없으므로,
 *       실제 조회 로직은 나중에 추가해야 합니다.
 *       
 * 구현 순서:
 * 1. price_target 테이블 생성
 * 2. PriceTarget 엔티티와 PriceTargetRepository 활성화
 * 3. 아래 주석 처리된 코드 활성화
 * 4. 목표가 설정/삭제 API 구현
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertService {

    private final PriceAlertEventProducer priceAlertEventProducer;
    
    // TODO: 나중에 주석 해제하고 의존성 주입
    // private final PriceTargetRepository priceTargetRepository;

    /**
     * 특정 종목의 현재가를 기준으로 목표가 조건을 평가합니다.
     * 
     * @param stockCode 종목 코드
     * @param currentPrice 현재가
     */
    public void evaluatePriceAlerts(String stockCode, BigDecimal currentPrice) {
        log.debug("Evaluating price alerts for stock: {}, currentPrice: {}", stockCode, currentPrice);
        
        try {
            // TODO: DB에서 해당 종목을 구독한 사용자들의 목표가 조회
            // 아래 코드의 주석을 해제하고 사용하세요
            
            /*
            // 1. 해당 종목에 대한 활성화된 목표가 목록 조회
            List<PriceTarget> priceTargets = priceTargetRepository.findByStockCodeAndEnabled(stockCode);
            
            log.debug("Found {} active price targets for stock: {}", priceTargets.size(), stockCode);
            
            // 2. 각 목표가에 대해 조건 평가
            for (PriceTarget target : priceTargets) {
                // 2-1. 상승 목표가 체크
                if (target.isUpperTargetReached(currentPrice)) {
                    createAndPublishAlert(
                        target.getUser().getId(),
                        stockCode,
                        target.getStock().getStockName(), // 종목명 추가
                        target.getUpperTarget(),
                        currentPrice,
                        PriceAlertEventDTO.AlertType.UPPER
                    );
                    
                    log.info("Upper price target reached - UserId: {}, StockCode: {}, Target: {}, Current: {}",
                            target.getUser().getId(), stockCode, target.getUpperTarget(), currentPrice);
                }
                
                // 2-2. 하락 목표가 체크
                if (target.isLowerTargetReached(currentPrice)) {
                    createAndPublishAlert(
                        target.getUser().getId(),
                        stockCode,
                        target.getStock().getStockName(), // 종목명 추가
                        target.getLowerTarget(),
                        currentPrice,
                        PriceAlertEventDTO.AlertType.LOWER
                    );
                    
                    log.info("Lower price target reached - UserId: {}, StockCode: {}, Target: {}, Current: {}",
                            target.getUser().getId(), stockCode, target.getLowerTarget(), currentPrice);
                }
            }
            */
            
            log.debug("Price alert evaluation completed for stock: {}", stockCode);
            
        } catch (Exception e) {
            log.error("Error evaluating price alerts for stock: {}, Error: {}", 
                    stockCode, e.getMessage(), e);
        }
    }

    /**
     * 목표가 알림 이벤트를 생성하고 Kafka로 발행합니다.
     * 
     * @param userId 사용자 ID
     * @param stockCode 종목 코드
     * @param stockName 종목명
     * @param triggerPrice 설정된 목표가
     * @param currentPrice 현재가
     * @param alertType 알림 타입 (UPPER/LOWER)
     */
    private void createAndPublishAlert(
            Long userId,
            String stockCode,
            String stockName,
            BigDecimal triggerPrice,
            BigDecimal currentPrice,
            PriceAlertEventDTO.AlertType alertType) {
        
        try {
            PriceAlertEventDTO event = PriceAlertEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .userId(userId)
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .triggerPrice(triggerPrice)
                    .currentPrice(currentPrice)
                    .alertType(alertType)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            priceAlertEventProducer.publishPriceAlertEvent(event);
            
            log.info("Price alert triggered - UserId: {}, StockCode: {}, AlertType: {}, TriggerPrice: {}, CurrentPrice: {}",
                    userId, stockCode, alertType, triggerPrice, currentPrice);
                    
        } catch (Exception e) {
            log.error("Error creating price alert event - UserId: {}, StockCode: {}, Error: {}", 
                    userId, stockCode, e.getMessage(), e);
        }
    }
}
