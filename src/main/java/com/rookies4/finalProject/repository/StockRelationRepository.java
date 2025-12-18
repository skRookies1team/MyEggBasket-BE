package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.StockRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockRelationRepository extends JpaRepository<StockRelation,Long> {
    List<StockRelation> findByFromStock(Stock fromStock);
    List<StockRelation> findByToStock(Stock toStock);

    // N+1 문제 해결: Fetch Join으로 fromStock과 toStock을 함께 조회
    @Query("SELECT sr FROM StockRelation sr " +
           "JOIN FETCH sr.fromStock " +
           "WHERE sr.fromStock = :fromStock")
    List<StockRelation> findByFromStockWithFetch(@Param("fromStock") Stock fromStock);

    // N+1 문제 해결: Fetch Join으로 fromStock과 toStock을 함께 조회
    @Query("SELECT sr FROM StockRelation sr " +
           "JOIN FETCH sr.toStock " +
           "WHERE sr.toStock = :toStock")
    List<StockRelation> findByToStockWithFetch(@Param("toStock") Stock toStock);
}
