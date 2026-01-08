package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.InterestStock;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // [추가] Query 어노테이션
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterestStockRepository extends JpaRepository<InterestStock, Long> {

    boolean existsByUserAndStock(User user, Stock stock);

    Optional<InterestStock> findByUserAndStock(User user, Stock stock);

    List<InterestStock> findByUserOrderByAddedAtDesc(User user);

    // [추가] 모든 관심 종목 코드 목록 조회 (중복 제거)
    @Query("SELECT DISTINCT i.stock.stockCode FROM InterestStock i")
    List<String> findAllInterestStockCodes();
}