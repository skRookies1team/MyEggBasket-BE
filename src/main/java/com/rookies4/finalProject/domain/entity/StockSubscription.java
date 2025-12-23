package com.rookies4.finalProject.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 종목 구독 엔티티
@Entity
@Table(name = "stock_subscription", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "stock_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 종목
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private Stock stock;

    // 종목명 (조회 편의를 위해 중복 저장)
    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    // 구독 시작 시간
    @Column(name = "subscribed_at", nullable = false, updatable = false)
    private LocalDateTime subscribedAt;

    @PrePersist
    protected void onCreate() {
        subscribedAt = LocalDateTime.now();
        // Stock 엔티티에서 종목명 자동 설정
        if (stock != null && stockName == null) {
            stockName = stock.getName();
        }
    }
}
