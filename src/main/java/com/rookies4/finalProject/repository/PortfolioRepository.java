package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    Optional<Portfolio> findByName(String name);
    boolean existsByName(String name);
}
