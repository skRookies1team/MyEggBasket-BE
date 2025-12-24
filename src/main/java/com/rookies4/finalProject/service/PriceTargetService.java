package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.PriceTarget;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.PriceTargetDTO;
import com.rookies4.finalProject.dto.kafka.PriceAlertEventDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.PriceTargetRepository;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.kafka.PriceAlertEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// 목표가 설정/관리 서비스
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PriceTargetService {

    private final PriceTargetRepository priceTargetRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final PriceAlertEventProducer priceAlertEventProducer;

    // 상한가 설정 (1개만 허용, 기존 것 있으면 덮어쓰기)
    public PriceTargetDTO.PriceTargetResponse setUpperTarget(PriceTargetDTO.SetTargetRequest request) {
        User user = getCurrentUser();
        Stock stock = getStock(request.getStockCode());

        // 현재 목표가 조회
        PriceTarget priceTarget = priceTargetRepository.findByUserAndStock(user, stock)
                .orElse(null);

        // 하한가가 설정되어 있으면 상한가 > 하한가 검증
        if (priceTarget != null && priceTarget.getLowerTarget() != null) {
            if (request.getTargetPrice().compareTo(priceTarget.getLowerTarget()) <= 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "상한가는 하한가보다 커야 합니다.");
            }
        }

        if (priceTarget == null) {
            // 새로 생성
            priceTarget = PriceTarget.builder()
                    .user(user)
                    .stock(stock)
                    .upperTarget(request.getTargetPrice())
                    .isEnabled(true)
                    .build();
        } else {
            // 기존 레코드 업데이트
            priceTarget.setUpperTarget(request.getTargetPrice());
            priceTarget.setIsEnabled(true);
        }

        PriceTarget saved = priceTargetRepository.save(priceTarget);
        log.info("Upper target set - UserId: {}, StockCode: {}, TargetPrice: {}",
                user.getId(), stock.getStockCode(), request.getTargetPrice());

        // Kafka 이벤트 발행
        publishPriceAlertEvent(user.getId(), stock.getStockCode(), stock.getName(),
                request.getTargetPrice(), PriceAlertEventDTO.AlertType.UPPER);

        return PriceTargetDTO.PriceTargetResponse.fromEntity(saved);
    }

    // 하한가 설정 (1개만 허용, 기존 것 있으면 덮어쓰기)
    public PriceTargetDTO.PriceTargetResponse setLowerTarget(PriceTargetDTO.SetTargetRequest request) {
        User user = getCurrentUser();
        Stock stock = getStock(request.getStockCode());

        // 현재 목표가 조회
        PriceTarget priceTarget = priceTargetRepository.findByUserAndStock(user, stock)
                .orElse(null);

        // 상한가가 설정되어 있으면 상한가 > 하한가 검증
        if (priceTarget != null && priceTarget.getUpperTarget() != null) {
            if (request.getTargetPrice().compareTo(priceTarget.getUpperTarget()) >= 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "하한가는 상한가보다 작아야 합니다.");
            }
        }

        if (priceTarget == null) {
            // 새로 생성
            priceTarget = PriceTarget.builder()
                    .user(user)
                    .stock(stock)
                    .lowerTarget(request.getTargetPrice())
                    .isEnabled(true)
                    .build();
        } else {
            // 기존 레코드 업데이트
            priceTarget.setLowerTarget(request.getTargetPrice());
            priceTarget.setIsEnabled(true);
        }

        PriceTarget saved = priceTargetRepository.save(priceTarget);
        log.info("Lower target set - UserId: {}, StockCode: {}, TargetPrice: {}",
                user.getId(), stock.getStockCode(), request.getTargetPrice());

        // Kafka 이벤트 발행
        publishPriceAlertEvent(user.getId(), stock.getStockCode(), stock.getName(),
                request.getTargetPrice(), PriceAlertEventDTO.AlertType.LOWER);

        return PriceTargetDTO.PriceTargetResponse.fromEntity(saved);
    }

    // 상한가 취소
    public void clearUpperTarget(String stockCode) {
        User user = getCurrentUser();
        Stock stock = getStock(stockCode);

        PriceTarget priceTarget = priceTargetRepository.findByUserAndStock(user, stock)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "설정된 목표가가 없습니다."));

        if (priceTarget.getUpperTarget() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "설정된 상한가가 없습니다.");
        }

        BigDecimal clearedTarget = priceTarget.getUpperTarget();
        priceTarget.setUpperTarget(null);

        // 상한가와 하한가 모두 없으면 레코드 삭제
        if (priceTarget.getLowerTarget() == null) {
            priceTargetRepository.delete(priceTarget);
            log.info("Price target deleted (both targets removed) - UserId: {}, StockCode: {}",
                    user.getId(), stockCode);
        } else {
            priceTargetRepository.save(priceTarget);
            log.info("Upper target cleared - UserId: {}, StockCode: {}", user.getId(), stockCode);
        }

        // Kafka 이벤트 발행 (취소 알림)
        publishPriceAlertCancelEvent(user.getId(), stock.getStockCode(), stock.getName(),
                clearedTarget, PriceAlertEventDTO.AlertType.UPPER);
    }

    // 하한가 취소
    public void clearLowerTarget(String stockCode) {
        User user = getCurrentUser();
        Stock stock = getStock(stockCode);

        PriceTarget priceTarget = priceTargetRepository.findByUserAndStock(user, stock)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "설정된 목표가가 없습니다."));

        if (priceTarget.getLowerTarget() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "설정된 하한가가 없습니다.");
        }

        BigDecimal clearedTarget = priceTarget.getLowerTarget();
        priceTarget.setLowerTarget(null);

        // 상한가와 하한가 모두 없으면 레코드 삭제
        if (priceTarget.getUpperTarget() == null) {
            priceTargetRepository.delete(priceTarget);
            log.info("Price target deleted (both targets removed) - UserId: {}, StockCode: {}",
                    user.getId(), stockCode);
        } else {
            priceTargetRepository.save(priceTarget);
            log.info("Lower target cleared - UserId: {}, StockCode: {}", user.getId(), stockCode);
        }

        // Kafka 이벤트 발행 (취소 알림)
        publishPriceAlertCancelEvent(user.getId(), stock.getStockCode(), stock.getName(),
                clearedTarget, PriceAlertEventDTO.AlertType.LOWER);
    }

    // 내 목표가 목록 조회
    @Transactional(readOnly = true)
    public List<PriceTargetDTO.PriceTargetResponse> getMyPriceTargets() {
        User user = getCurrentUser();

        return priceTargetRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(PriceTargetDTO.PriceTargetResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // 목표가 조회 (종목별)
    @Transactional(readOnly = true)
    public PriceTargetDTO.PriceTargetResponse getPriceTarget(String stockCode) {
        User user = getCurrentUser();
        Stock stock = getStock(stockCode);

        PriceTarget priceTarget = priceTargetRepository.findByUserAndStock(user, stock)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "해당 종목의 목표가가 설정되지 않았습니다."));

        return PriceTargetDTO.PriceTargetResponse.fromEntity(priceTarget);
    }

    private User getCurrentUser() {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "로그인한 사용자를 찾을 수 없습니다."));
    }

    private Stock getStock(String stockCode) {
        return stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND,
                        "종목을 찾을 수 없습니다: " + stockCode));
    }

    // Kafka 목표가 알림 이벤트 발행
    private void publishPriceAlertEvent(Long userId, String stockCode, String stockName,
                                        BigDecimal targetPrice, PriceAlertEventDTO.AlertType alertType) {
        try {
            PriceAlertEventDTO event = PriceAlertEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .userId(userId)
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .triggerPrice(targetPrice)
                    .currentPrice(targetPrice)
                    .alertType(alertType)
                    .eventType(PriceAlertEventDTO.EventType.SET)
                    .timestamp(LocalDateTime.now())
                    .build();

            priceAlertEventProducer.publishPriceAlertEvent(event);
            log.info("Price target event published - UserId: {}, StockCode: {}, AlertType: {}, TargetPrice: {}",
                    userId, stockCode, alertType, targetPrice);
        } catch (Exception e) {
            log.error("Failed to publish price target event - UserId: {}, StockCode: {}, Error: {}",
                    userId, stockCode, e.getMessage(), e);
        }
    }

    // Kafka 목표가 취소 이벤트 발행
    private void publishPriceAlertCancelEvent(Long userId, String stockCode, String stockName,
                                              BigDecimal canceledPrice, PriceAlertEventDTO.AlertType alertType) {
        try {
            PriceAlertEventDTO event = PriceAlertEventDTO.builder()
                    .eventId(UUID.randomUUID().toString())
                    .userId(userId)
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .triggerPrice(canceledPrice)
                    .currentPrice(null) // 취소 시에는 현재가 불필요
                    .alertType(alertType)
                    .eventType(PriceAlertEventDTO.EventType.CANCELED)
                    .timestamp(LocalDateTime.now())
                    .build();

            priceAlertEventProducer.publishPriceAlertEvent(event);
            log.info("Price target cancel event published - UserId: {}, StockCode: {}, AlertType: {}, CanceledPrice: {}",
                    userId, stockCode, alertType, canceledPrice);
        } catch (Exception e) {
            log.error("Failed to publish price target cancel event - UserId: {}, StockCode: {}, Error: {}",
                    userId, stockCode, e.getMessage(), e);
        }
    }
}
