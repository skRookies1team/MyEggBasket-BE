package com.rookies4.finalProject.domain.entity;

import com.rookies4.finalProject.domain.enums.RecommendationAction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_recommendations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reco_id")
    private Long recoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code")
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 10)
    private RecommendationAction actionType; // BUY, SELL, HOLD

    @Column(name = "ai_score")
    private Float aiScore; // AI 점수 (0 ~ 100)

    @Column(name = "current_holding", precision = 20, scale = 2)
    private BigDecimal currentHolding; // 현재 보유량

    @Column(name = "target_holding", precision = 20, scale = 2)
    private BigDecimal targetHolding; // 목표 보유량

    @Column(name = "adjustment_amount", precision = 20, scale = 2)
    private BigDecimal adjustmentAmount; // 조절 금액 (+/-)

    @Column(name = "target_holding_percentage")
    private Float targetHoldingPercentage; // 목표 보유 비율 (%)

    @Column(name = "reason_summary", columnDefinition = "TEXT")
    private String reasonSummary; // 근거

    @Column(name = "risk_warning", columnDefinition = "TEXT")
    private String riskWarning; // 리스크 경고 문구

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "AIRecommendation{" +
                "recoId=" + recoId +
                ", portfolioId=" + (portfolio != null ? portfolio.getPortfolioId() : null) +
                ", stockCode=" + (stock != null ? stock.getStockCode() : null) +
                ", actionType='" + actionType + '\'' +
                ", aiScore=" + aiScore +
                ", createdAt=" + createdAt +
                '}';
    }
}