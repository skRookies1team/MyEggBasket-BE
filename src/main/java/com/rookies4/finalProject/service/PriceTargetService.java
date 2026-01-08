package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.PriceTarget;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.PriceTargetDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.PriceTargetRepository;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

// 목표가 관리 서비스
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PriceTargetService {

    private final PriceTargetRepository priceTargetRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;

    private static final int ALERT_COOLDOWN_MINUTES = 30;

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

            // 목표가 재설정 시 상한 알림 상태 초기화
            priceTarget.setUpperTriggered(false);
            priceTarget.setUpperTriggeredAt(null);
        }

        PriceTarget saved = priceTargetRepository.save(priceTarget);

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

            // 목표가 재설정 시 하한 알림 상태 초기화
            priceTarget.setLowerTriggered(false);
            priceTarget.setLowerTriggeredAt(null);
        }

        PriceTarget saved = priceTargetRepository.save(priceTarget);

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
        }
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
        }
    }

    // 내 목표가 목록 조회
    @Transactional(readOnly = true)
    public List<PriceTargetDTO.PriceTargetResponse> getMyPriceTargets() {
        User user = getCurrentUser();

        List<PriceTargetDTO.PriceTargetResponse> targets = priceTargetRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(PriceTargetDTO.PriceTargetResponse::fromEntity)
                .collect(Collectors.toList());

        log.info("[PriceTarget] 내 목표가 목록 조회 성공 - UserId: {}, Count: {}", user.getId(), targets.size());
        return targets;
    }

    // 목표가 조회 (종목별)
    @Transactional(readOnly = true)
    public PriceTargetDTO.PriceTargetResponse getPriceTarget(String stockCode) {
        User user = getCurrentUser();
        Stock stock = getStock(stockCode);

        PriceTarget priceTarget = priceTargetRepository.findByUserAndStock(user, stock)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "해당 종목의 목표가가 설정되지 않았습니다."));

        log.info("[PriceTarget] 목표가 조회 성공 - UserId: {}, StockCode: {}", user.getId(), stockCode);
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

    // 실시간 체결가 수신 시 목표가 도달 여부 판단
    public List<PriceTarget> evaluate(String stockCode, BigDecimal currentPrice) {
        if (stockCode == null || currentPrice == null) {
            return List.of();
        }

        List<PriceTarget> targets =
                priceTargetRepository.findByStockCodeAndEnabled(stockCode);

        List<PriceTarget> reachedTargets = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (PriceTarget target : targets) {
            if (checkUpperTarget(target, currentPrice, now)) {
                reachedTargets.add(target);
            }
            if (checkLowerTarget(target, currentPrice, now)) {
                reachedTargets.add(target);
            }
        }

        return reachedTargets;
    }

    private boolean checkUpperTarget(
            PriceTarget target,
            BigDecimal currentPrice,
            LocalDateTime now
    ) {
        if (target.getUpperTarget() == null) return false;
        if (currentPrice.compareTo(target.getUpperTarget()) < 0) return false;

        if (Boolean.TRUE.equals(target.getUpperTriggered())
                && target.getUpperTriggeredAt() != null
                && target.getUpperTriggeredAt()
                .plusMinutes(ALERT_COOLDOWN_MINUTES)
                .isAfter(now)) {
            return false;
        }

        target.setUpperTriggered(true);
        target.setUpperTriggeredAt(now);
        priceTargetRepository.save(target);
        return true;
    }

    private boolean checkLowerTarget(
            PriceTarget target,
            BigDecimal currentPrice,
            LocalDateTime now
    ) {
        if (target.getLowerTarget() == null) return false;
        if (currentPrice.compareTo(target.getLowerTarget()) > 0) return false;

        if (Boolean.TRUE.equals(target.getLowerTriggered())
                && target.getLowerTriggeredAt() != null
                && target.getLowerTriggeredAt()
                .plusMinutes(ALERT_COOLDOWN_MINUTES)
                .isAfter(now)) {
            return false;
        }

        target.setLowerTriggered(true);
        target.setLowerTriggeredAt(now);
        priceTargetRepository.save(target);
        return true;
    }
}