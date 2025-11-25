package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByTicker(String ticker);
    boolean existsByTicker(String ticker);
}
