package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.PriceTarget;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// 목표가 Repository
@Repository
public interface PriceTargetRepository extends JpaRepository<PriceTarget, Long> {

    // 특정 종목의 활성화된 목표가 조회 (실시간 체결 데이터 받았을 때 모니터링 사용자 조회)
    @Query("SELECT pt FROM PriceTarget pt " +
           "JOIN FETCH pt.user " +
           "JOIN FETCH pt.stock " +
           "WHERE pt.stock.stockCode = :stockCode " +
           "AND pt.isEnabled = true")
    List<PriceTarget> findByStockCodeAndEnabled(@Param("stockCode") String stockCode);

    // 특정 사용자의 모든 목표가 조회
    List<PriceTarget> findByUserOrderByCreatedAtDesc(User user);

    // 특정 사용자와 종목에 대한 목표가 조회
    Optional<PriceTarget> findByUserAndStock(User user, Stock stock);

    // 특정 사용자와 종목에 대한 목표가 존재 여부
    boolean existsByUserAndStock(User user, Stock stock);
}

