package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Transaction;
import com.rookies4.finalProject.domain.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // 1. 사용자 ID + 주문 상태로 조회 (파라미터가 있을 때 사용)
    List<Transaction> findByUser_IdAndStatusOrderByExecutedAtDesc(Long userId, TransactionStatus status);

    // 2. 사용자 ID로 전체 조회 (파라미터가 없을 때 사용)
    List<Transaction> findByUser_IdOrderByExecutedAtDesc(Long userId);
}