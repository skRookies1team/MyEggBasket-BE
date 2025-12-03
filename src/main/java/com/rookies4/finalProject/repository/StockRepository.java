// MyEggBasket-BE/src/main/java/com/rookies4/finalProject/repository/StockRepository.java
package com.rookies4.finalProject.repository;

import com.rookies4.finalProject.domain.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, String> {
    Optional<Stock> findByStockCode(String stockCode);
    boolean existsByStockCode(String stockCode);

    // [추가] 종목명 또는 종목코드로 검색 (대소문자 무시)
    List<Stock> findByNameContainingIgnoreCaseOrStockCodeContaining(String name, String stockCode);
}