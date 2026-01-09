package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    Optional<Portfolio> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndUser(String name, User user);

    @EntityGraph(attributePaths = "holdings")
    List<Portfolio> findByUser(User user);

    // [추가] 포트폴리오 상세 조회 시 N+1 문제 해결을 위한 EntityGraph
    // holdings와 그 안의 stock, 그리고 owner인 user까지 한 번에 가져옴
    @EntityGraph(attributePaths = { "holdings", "holdings.stock", "user" })
    @Query("SELECT p FROM Portfolio p WHERE p.portfolioId = :id")
    Optional<Portfolio> findByIdWithDetails(@Param("id") Long id);
}
