package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Transaction;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.domain.enums.TransactionStatus;
import com.rookies4.finalProject.dto.TransactionDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.TransactionRepository;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final TransactionSyncService transactionSyncService; // 주문 조회용

    /**
     * 사용자 주문/거래 내역 조회
     * - 요청 시마다 KIS 와 동기화 시도
     * - 동기화 실패해도 DB에 있는 데이터는 그대로 반환
     */
    @Transactional(readOnly = true)
    public List<TransactionDTO.Response> getUserOrders(Long userId, String statusParam) {

        // 1. 권한 확인
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (!currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "해당 사용자의 주문 내역에 접근할 권한이 없습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "해당 ID의 사용자를 찾을 수 없습니다.")
                );

        // 2. KIS API 동기화 (주문 내역 조회 시마다 갱신)
        try {
            transactionSyncService.syncUserOrdersFromKis(user);
        } catch (Exception e) {
            log.warn("KIS 주문 내역 동기화 실패, DB 데이터만 사용. userId={}, msg={}",
                    userId, e.getMessage());
        }

        // 3. DB 조회 (동기화 끝난 최신 데이터 조회)
        TransactionStatus statusFilter = null;
        if (statusParam != null && !statusParam.isBlank()) {
            statusFilter = parseStatus(statusParam);
        }

        List<Transaction> transactions;
        if (statusParam != null) {
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
     * 쿼리스트링 status 값을 enum 으로 변환 - pending / PENDING - completed / COMPLETE / COMPLETED - canceled / cancelled /
     * CANCELED / CANCELLED
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
}
