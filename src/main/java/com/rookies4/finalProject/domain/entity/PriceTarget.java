package com.rookies4.finalProject.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 목표가 설정 엔티티
@Entity
@Table(
    name = "price_target",
    // 동일 사용자-종목에 대해 하나의 레코드만 허용 (상/하한가를 한 행에 보관)
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_price_target_user_stock", columnNames = {"user_id", "stock_code"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //사용자 (목표가를 설정한 사용자)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 종목
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private Stock stock;

    /**
    상승 목표가 (이 가격 이상이 되면 알림 - 매도 신호)
    NULL 가능 (설정하지 않을 수 있음)
     */
    @Column(name = "upper_target", precision = 19, scale = 2)
    private BigDecimal upperTarget;

    /**
     * 하락 목표가 (이 가격 이하가 되면 알림 - 매수 신호)
     * NULL 가능 (설정하지 않을 수 있음)
     */
    @Column(name = "lower_target", precision = 19, scale = 2)
    private BigDecimal lowerTarget;

    /**
     * 활성화 여부
     * false인 경우 알림을 보내지 않음
     */
    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;

    // 생성 시간
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 수정 시간
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 상승 목표가가 현재가 이상인지 체크
    public boolean isUpperTargetReached(BigDecimal currentPrice) {
        return upperTarget != null && 
               isEnabled && 
               currentPrice.compareTo(upperTarget) >= 0;
    }

    // 하락 목표가가 현재가 이하인지 체크
    public boolean isLowerTargetReached(BigDecimal currentPrice) {
        return lowerTarget != null && 
               isEnabled && 
               currentPrice.compareTo(lowerTarget) <= 0;
    }
}
