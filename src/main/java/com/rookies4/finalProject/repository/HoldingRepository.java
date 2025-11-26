package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Holding;
import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    List<Holding> findByPortfolio(Portfolio portfolio);
    Optional<Holding> findByPortfolioAndHoldingId(Portfolio portfolio, Long holdingId);
    Optional<Holding> findByPortfolioAndStock(Portfolio portfolio, Stock stock);
}
