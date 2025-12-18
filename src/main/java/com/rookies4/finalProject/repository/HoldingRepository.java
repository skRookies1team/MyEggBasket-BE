package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Holding;
import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    // @BatchSize는 컬렉션에만 적용 가능하므로 Fetch Join 필요
    List<Holding> findByPortfolio(Portfolio portfolio);
    Optional<Holding> findByPortfolioAndHoldingId(Portfolio portfolio, Long holdingId);
    Optional<Holding> findByPortfolioAndStock(Portfolio portfolio, Stock stock);

    // N+1 문제 해결: Fetch Join으로 stock을 함께 조회
    @Query("SELECT h FROM Holding h " +
           "JOIN FETCH h.stock " +
           "WHERE h.portfolio = :portfolio")
    List<Holding> findByPortfolioWithStock(@Param("portfolio") Portfolio portfolio);
}
