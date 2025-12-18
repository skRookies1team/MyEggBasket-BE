package com.rookies4.finalProject.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 히스토리 리포트 엔티티
 * 포트폴리오의 주간/월간 성과를 기록하는 테이블
 * RQ-43, RQ-44, RQ-45, RQ-46, RQ-47, RQ-48 요구사항 지원
 */
@Entity
@Table(name = "history_reports", indexes = {
        @Index(name = "idx_portfolio_period", columnList = "portfolio_id, report_period, period_start_date"),
        @Index(name = "idx_period_date", columnList = "period_start_date, period_end_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoryReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "report_period", length = 20)
    private String reportPeriod; // 예: "WEEKLY", "MONTHLY"

    @Column(name = "period_start_date")
    private LocalDate periodStartDate;

    @Column(name = "period_end_date")
    private LocalDate periodEndDate;

    // 총 수익률 (%)
    @Column(name = "total_return_rate", nullable = false)
    private Float totalReturnRate;

    // AI 추천 성공률 (%)
    @Column(name = "success_rate")
    private Float successRate;

    @Override
    public String toString() {
        return "HistoryReport{" +
                "historyId=" + historyId +
                ", portfolioId=" + (portfolio != null ? portfolio.getPortfolioId() : null) +
                ", totalReturnRate=" + totalReturnRate +
                ", successRate=" + successRate +
                '}';
    }
}