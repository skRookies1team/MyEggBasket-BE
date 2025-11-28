package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.InterestStock;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterestStockRepository extends JpaRepository<InterestStock, Long> {
    boolean existsByUserAndStock(User user, Stock stock);
    Optional<InterestStock> findByUserAndStock(User user, Stock stock);
    List<InterestStock> findByUserOrderByAddedAtDesc(User user);
}
