package com.rookies4.finalProject.domain.entity;

import com.rookies4.finalProject.domain.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "portfolios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "portfolio_id")
    private Long portfolioId;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "total_asset", precision = 20, scale = 2)
    private BigDecimal totalAsset; // 총 자산 (현금 + 평가금)

    @Column(name = "cash_balance", precision = 20, scale = 2)
    private BigDecimal cashBalance; // 예수금 (사용 가능한 현금)

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    // 포트폴리오에 속한 보유 종목들
    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Holding> holdings = new ArrayList<>();

    // 포트폴리오에 대한 AI 추천들
    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AIRecommendation> recommendations = new ArrayList<>();

    // 히스토리 리포트 목록
    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<HistoryReport> historyReports = new ArrayList<>();

    // 연관관계 편의 메서드
    public void addHolding(Holding holding) {
        holdings.add(holding);
        holding.setPortfolio(this);
    }

    public void addRecommendation(AIRecommendation recommendation) {
        recommendations.add(recommendation);
        recommendation.setPortfolio(this);
    }

    // 연관관계 편의 메서드
    public void addHistoryReport(HistoryReport report) {
        historyReports.add(report);
        report.setPortfolio(this);
    }

    @Override
    public String toString() {
        return "Portfolio{" +
                "portfolioId=" + portfolioId +
                ", userId=" + (user != null ? user.getId() : null) +
                ", name='" + name + '\'' +
                ", totalAsset=" + totalAsset +
                ", riskLevel='" + riskLevel + '\'' +
                '}';
    }
}