package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.AIRecommendation;
import com.rookies4.finalProject.domain.entity.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AIRecommendationRepository extends JpaRepository<AIRecommendation, Long> {
    Page<AIRecommendation> findByPortfolio(Portfolio portfolio, Pageable pageable);

    // [추가] 특정 사용자(SecurityUtil에서 가져옴)의 포트폴리오 중 최근(since)에 생성된
    // 'HOLD'가 아닌 추천이 있는 포트폴리오 ID 목록 조회
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r.portfolio.portfolioId FROM AIRecommendation r " +
            "WHERE r.portfolio.user.id = :userId " +
            "AND r.createdAt >= :since " +
            "AND r.actionType != 'HOLD'")
    java.util.List<Long> findDistinctPortfolioIdsWithActiveRecommendations(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
}
