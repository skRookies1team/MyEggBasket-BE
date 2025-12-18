package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    Optional<Portfolio> findByName(String name);

    boolean existsByName(String name);
    
    // 목록 조회: 포트폴리오만 필요한 경우 (holdings 접근 X)
    // N+1 방지: @BatchSize로 holdings 접근 시 IN 절 사용
    List<Portfolio> findByUser(User user);
    
    boolean existsByNameAndUser(String name, User user);

    // 상세 조회: holdings과 stock 모두 필요한 경우
    // Fetch Join으로 한 번의 쿼리로 모든 데이터 로드
    @Query("SELECT p FROM Portfolio p " +
           "LEFT JOIN FETCH p.holdings h " +
           "LEFT JOIN FETCH h.stock " +
           "WHERE p.portfolioId = :portfolioId")
    Optional<Portfolio> findByIdWithHoldings(@Param("portfolioId") Long portfolioId);
}
