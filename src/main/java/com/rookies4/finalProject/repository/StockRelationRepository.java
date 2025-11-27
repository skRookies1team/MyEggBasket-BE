package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.StockRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockRelationRepository extends JpaRepository<StockRelation,Long> {
    List<StockRelation> findByFromStock(Stock fromStock);
    List<StockRelation> findByToStock(Stock toStock);
}
