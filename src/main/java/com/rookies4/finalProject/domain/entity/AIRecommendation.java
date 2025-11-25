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
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 10)
    private RecommendationAction actionType; // BUY, SELL, HOLD

    @Column(name = "confidence_score")
    private Float confidenceScore; // AI 확신도 (0.0 ~ 1.0)

    @Column(name = "suggested_price", precision = 20, scale = 2)
    private BigDecimal suggestedPrice; // 추천 진입가

    @Column(name = "stop_loss_price", precision = 20, scale = 2)
    private BigDecimal stopLossPrice; // 손절가

    @Column(name = "reason_summary", columnDefinition = "TEXT")
    private String reasonSummary; // "3줄 요약" 근거

    @Column(name = "risk_warning", columnDefinition = "TEXT")
    private String riskWarning; // 리스크 경고 문구

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "valid_until")
    private LocalDateTime validUntil; // 유효 시간 (스캘핑의 경우 짧게)

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "AIRecommendation{" +
                "recoId=" + recoId +
                ", portfolioId=" + (portfolio != null ? portfolio.getPortfolioId() : null) +
                ", stockId=" + (stock != null ? stock.getStockId() : null) +
                ", actionType='" + actionType + '\'' +
                ", confidenceScore=" + confidenceScore +
                ", createdAt=" + createdAt +
                '}';
    }
}