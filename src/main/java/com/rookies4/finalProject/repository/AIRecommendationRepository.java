package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.AIRecommendation;
import com.rookies4.finalProject.domain.entity.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AIRecommendationRepository extends JpaRepository<AIRecommendation, Long> {
    Page<AIRecommendation> findByPortfolio(Portfolio portfolio, Pageable pageable);
}
