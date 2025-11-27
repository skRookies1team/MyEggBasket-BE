package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Transaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // 특정 사용자의 거래 내역을 최신순으로 조회
    List<Transaction> findByUser_IdOrderByExecutedAtDesc(Long userId);
}
