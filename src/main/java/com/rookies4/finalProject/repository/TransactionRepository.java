package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Transaction;
import com.rookies4.finalProject.domain.enums.TransactionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // 1. 사용자 ID + 주문 상태로 조회
    List<Transaction> findByUser_IdAndStatusOrderByExecutedAtDesc(Long userId, TransactionStatus status);

    // 2. 사용자 ID로 전체 조회
    List<Transaction> findByUser_IdOrderByExecutedAtDesc(Long userId);

    // 2-1. 사용자 ID + 포트폴리오로 조회
    List<Transaction> findByUser_IdAndPortfolios_PortfolioIdOrderByExecutedAtDesc(Long userId, Long portfolioId);

    // 2-2. 사용자 ID + 포트폴리오 + 상태로 조회
    List<Transaction> findByUser_IdAndPortfolios_PortfolioIdAndStatusOrderByExecutedAtDesc(Long userId, Long portfolioId, TransactionStatus status);

    // 3. 사용자 ID와 KIS 주문번호(orderNo)로 거래 내역 찾기
    Optional<Transaction> findByUser_IdAndOrderNo(Long userId, String orderNo);

    // 4. 사용자 ID + 종목 코드로 조회 (포트폴리오 할당용)
    List<Transaction> findByUser_IdAndStock_StockCodeOrderByExecutedAtDesc(Long userId, String stockCode);

    // 4-1. 사용자 ID + 종목 코드 + 상태로 조회 (포트폴리오 할당용)
    List<Transaction> findByUser_IdAndStock_StockCodeAndStatusOrderByExecutedAtDesc(Long userId, String stockCode, TransactionStatus status);

    // 종목 코드 리스트에 포함된 거래 내역 조회 (상태 필터링 포함)
    List<Transaction> findByUser_IdAndStock_StockCodeInAndStatusOrderByExecutedAtDesc(
            Long userId, List<String> stockCodes, TransactionStatus status);

    // 종목 코드 리스트에 포함된 모든 거래 내역 조회
    List<Transaction> findByUser_IdAndStock_StockCodeInOrderByExecutedAtDesc(
            Long userId, List<String> stockCodes);
}