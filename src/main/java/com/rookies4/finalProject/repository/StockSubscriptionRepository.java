package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.StockSubscription;
import com.rookies4.finalProject.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// 종목 구독 Repository
@Repository
public interface StockSubscriptionRepository extends JpaRepository<StockSubscription, Long> {

    // 특정 사용자의 구독 종목 목록 조회
    List<StockSubscription> findByUserOrderBySubscribedAtDesc(User user);

    // 특정 종목을 구독 중인 사용자 목록 조회
    List<StockSubscription> findByStock_StockCode(String stockCode);

    // 사용자 + 종목으로 구독 정보 조회
    Optional<StockSubscription> findByUserAndStock(User user, Stock stock);

    // 사용자가 특정 종목을 구독 중인지 확인
    boolean existsByUserAndStock(User user, Stock stock);

    // 모든 구독 중인 종목 코드 목록 (중복 제거)
    @Query("SELECT DISTINCT s.stock.stockCode FROM StockSubscription s")
    List<String> findAllSubscribedStockCodes();
}
