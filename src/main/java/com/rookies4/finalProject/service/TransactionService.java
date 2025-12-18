package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Transaction;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.domain.enums.TransactionStatus;
import com.rookies4.finalProject.dto.TransactionDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.TransactionRepository;
import com.rookies4.finalProject.repository.UserRepository;

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
    private final TransactionSyncService transactionSyncService;

    /**
     * 사용자 주문/거래 내역 조회
     * - 요청 시마다 KIS 와 동기화 시도
     * - 동기화 실패해도 DB에 있는 데이터는 그대로 반환
     */
    @Transactional(readOnly = true)
    public List<TransactionDTO.Response> getUserOrders(Long userId, String status, boolean useVirtualServer) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "해당 ID의 사용자를 찾을 수 없습니다.")
                );

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
        if (statusFilter != null) {
            // N+1 문제 해결: Fetch Join으로 user를 함께 조회
            transactions = transactionRepository
                    .findByUser_IdAndStatusWithFetch(userId, statusFilter);
        } else {
            // N+1 문제 해결: Fetch Join으로 user를 함께 조회
            transactions = transactionRepository
                    .findByUser_IdWithFetch(userId);
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
}
