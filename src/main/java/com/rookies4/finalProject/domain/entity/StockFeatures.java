package com.rookies4.finalProject.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_features")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockFeatures {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feature_id")
    private Long featureId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(name = "reference_date")
    private LocalDateTime referenceDate; // 기준 시점

    // === NLP 파생 피처 ===
    @Column(name = "sentiment_score")
    private Float sentimentScore; // FinBERT 감성 점수 (-1.0 ~ 1.0)

//    @Column(name = "news_velocity")
//    private Float newsVelocity; // 뉴스 발생 빈도 (화제성)

    // === GNN 파생 피처 ===
    @Column(name = "centrality_score")
    private Float centralityScore; // 중심성 (시장에서 얼마나 중요한가)

//    @Column(name = "propagation_score")
//    private Float propagationScore; // 파급력 지수

    // === 기술적 지표 ===
    @Column(name = "volatility_atr")
    private Float volatilityAtr; // 변동성 (Average True Range)

    @Column(name = "risk_score")
    private Float riskScore; // 통합 리스크 점수 (0.0 ~ 1.0)

    @Override
    public String toString() {
        return "StockFeatures{" +
                "featureId=" + featureId +
                ", stockId=" + (stock != null ? stock.getStockId() : null) +
                ", referenceDate=" + referenceDate +
                ", sentimentScore=" + sentimentScore +
                ", centralityScore=" + centralityScore +
                ", riskScore=" + riskScore +
                '}';
    }
}