package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    Optional<Portfolio> findByName(String name);

    boolean existsByName(String name);
    
    List<Portfolio> findByUser(User user);
    
    boolean existsByNameAndUser(String name, User user);
}
