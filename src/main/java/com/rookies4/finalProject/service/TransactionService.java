package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.Transaction;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.domain.enums.TransactionStatus;
import com.rookies4.finalProject.domain.enums.TransactionType;
import com.rookies4.finalProject.domain.enums.TriggerSource;
import com.rookies4.finalProject.dto.TransactionDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.PortfolioRepository;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.repository.TransactionRepository;
import com.rookies4.finalProject.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final TransactionSyncService transactionSyncService;
    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;

    /**
     * 사용자 주문/거래 내역 조회
     * - 요청 시마다 KIS 와 동기화 시도
     * - 동기화 실패해도 DB에 있는 데이터는 그대로 반환
     */
    @Transactional(readOnly = true)
    public List<TransactionDTO.Response> getUserOrders(Long userId, String status, boolean useVirtualServer) {

        return getUserOrders(userId, status, useVirtualServer, null);
    }

    @Transactional(readOnly = true)
    public List<TransactionDTO.Response> getUserOrders(Long userId, String status, boolean useVirtualServer, Long portfolioId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "해당 ID의 사용자를 찾을 수 없습니다.")
                );

        Portfolio portfolio = null;
        if (portfolioId != null) {
            portfolio = portfolioRepository.findById(portfolioId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

            if (!portfolio.getUser().getId().equals(userId)) {
                throw new BusinessException(
                        ErrorCode.AUTH_ACCESS_DENIED,
                        "해당 포트폴리오에 대한 접근 권한이 없습니다.");
            }
        }

        // 2. KIS API 동기화 (주문 내역 조회 시마다 갱신)
        try {
            transactionSyncService.syncUserOrdersFromKis(user, useVirtualServer);
        } catch (Exception e) {
            log.warn("KIS 주문 내역 동기화 실패, DB 데이터만 사용. userId={}, msg={}",
                    userId, e.getMessage());
        }

        // 3. DB 조회 (동기화 끝난 최신 데이터 조회)
        TransactionStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            statusFilter = parseStatus(status);
        }

        List<Transaction> transactions;
        if (portfolio != null && statusFilter != null) {
            transactions = transactionRepository
                    .findByUser_IdAndPortfolios_PortfolioIdAndStatusOrderByExecutedAtDesc(userId, portfolioId, statusFilter);
        } else if (portfolio != null) {
            transactions = transactionRepository
                    .findByUser_IdAndPortfolios_PortfolioIdOrderByExecutedAtDesc(userId, portfolioId);
        } else if (statusFilter != null) {
            transactions = transactionRepository
                    .findByUser_IdAndStatusOrderByExecutedAtDesc(userId, statusFilter);
        } else {
            transactions = transactionRepository
                    .findByUser_IdOrderByExecutedAtDesc(userId);
        }

        // 4. DTO 변환
        return transactions.stream()
                .map(TransactionDTO.Response::fromEntity)
                .toList();
    }

    /**
     * 쿼리스트링 status 값을 enum 으로 변환
     * - pending / PENDING
     * - completed / COMPLETE / COMPLETED
     * - canceled / cancelled / CANCELED / CANCELLED
     */
    private TransactionStatus parseStatus(String raw) {
        String v = raw.trim().toUpperCase();

        if (v.equals("PENDING")) {
            return TransactionStatus.PENDING;
        } else if (v.equals("COMPLETED") || v.equals("COMPLETE")) {
            return TransactionStatus.COMPLETED;
        } else if (v.equals("CANCELLED") || v.equals("CANCELED")) {
            return TransactionStatus.CANCELLED;
        } else {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "유효하지 않은 주문 상태값입니다: " + raw
            );
        }
    }

    /**
     * KIS 주문 요청 직후 DB에 거래 초안(PENDING)을 기록해 orderNo-portfolio 매핑을 남긴다.
     * 이후 동기화 시 동일 orderNo 로 upsert 되며 포트폴리오 정보가 유지된다.
     */
    @Transactional
    public void recordLocalOrder(
            User user,
            TransactionType orderType,
            TriggerSource triggerSource,
            String stockCode,
            Integer quantity,
            Integer price,
            Long portfolioId,
            String orderNo
    ) {
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자 정보가 없습니다.");
        }
        if (orderNo == null || orderNo.isBlank()) {
            log.warn("[Transaction] orderNo 누락으로 거래 기록을 건너뜁니다. userId={}, stockCode={}", user.getId(), stockCode);
            return;
        }

        Portfolio portfolio = null;
        if (portfolioId != null) {
            portfolio = portfolioRepository.findById(portfolioId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

            if (!portfolio.getUser().getId().equals(user.getId())) {
                throw new BusinessException(
                        ErrorCode.AUTH_ACCESS_DENIED,
                        "해당 포트폴리오에 대한 접근 권한이 없습니다.");
            }
        }

        Stock stock = null;
        if (stockCode != null && !stockCode.isBlank()) {
            stock = stockRepository.findById(stockCode)
                    .orElseGet(() -> stockRepository.save(Stock.builder()
                            .stockCode(stockCode)
                            .name("")
                            .build()));
        }

        Transaction transaction = transactionRepository.findByUser_IdAndOrderNo(user.getId(), orderNo)
                .orElseGet(() -> Transaction.builder()
                        .user(user)
                        .orderNo(orderNo)
                        .build());

        if (portfolio != null) {
            if (transaction.getPortfolios() == null) {
                transaction.setPortfolios(new java.util.ArrayList<>());
            }
            if (!transaction.getPortfolios().contains(portfolio)) {
                transaction.getPortfolios().add(portfolio);
            }
        }
        if (stock != null) {
            transaction.setStock(stock);
        }
        if (orderType != null) {
            transaction.setType(orderType);
        }
        if (quantity != null) {
            transaction.setQuantity(quantity);
        }
        if (transaction.getFilledQuantity() == null) {
            transaction.setFilledQuantity(0);
        }
        if (price != null) {
            transaction.setPrice(BigDecimal.valueOf(price));
        }
        if (triggerSource != null) {
            transaction.setTriggerSource(triggerSource);
        }
        transaction.setStatus(TransactionStatus.PENDING);
        if (transaction.getExecutedAt() == null) {
            transaction.setExecutedAt(LocalDateTime.now());
        }

        transactionRepository.save(transaction);
        log.info("[Transaction] 주문 기록 저장 완료 userId={}, orderNo={}, portfolioId={}", user.getId(), orderNo, portfolioId);
    }
}
