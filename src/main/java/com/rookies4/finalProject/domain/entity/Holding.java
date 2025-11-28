package com.rookies4.finalProject.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "holdings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"portfolio", "hibernateLazyInitializer", "handler"})
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "holding_id")
    private Long holdingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code")
    private Stock stock;

    @Column(name = "quantity", nullable = false)
    private Integer quantity; // 보유 수량

    @Column(name = "avg_price", precision = 20, scale = 2)
    private BigDecimal avgPrice; // 평단가 (평균 매수가)

    @Column(name = "current_weight")
    private Float currentWeight; // 현재 비중 (매일 업데이트, 0.0 ~ 1.0)

    @Column(name = "target_weight")
    private Float targetWeight; // 목표 비중 (리밸런싱 타겟, 0.0 ~ 1.0)

    @Override
    public String toString() {
        return "Holding{" +
                "holdingId=" + holdingId +
                ", portfolioId=" + (portfolio != null ? portfolio.getPortfolioId() : null) +
                ", stockCode=" + (stock != null ? stock.getStockCode() : null) +
                ", quantity=" + quantity +
                ", avgPrice=" + avgPrice +
                ", currentWeight=" + currentWeight +
                '}';
    }
}