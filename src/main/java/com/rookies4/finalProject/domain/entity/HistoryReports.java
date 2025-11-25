package com.rookies4.finalProject.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
public class HistoryReports {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

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