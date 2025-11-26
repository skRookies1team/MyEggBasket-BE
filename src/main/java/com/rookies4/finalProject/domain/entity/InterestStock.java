package com.rookies4.finalProject.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interest_stocks", indexes = {
        @Index(name = "idx_user_stock", columnList = "user_id, stock_id", unique = true),
        @Index(name = "idx_user_added", columnList = "user_id, added_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterestStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interest_id")
    private Long interestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    // 사용자 지정 정렬 순서 (옵션)
    @Column(name = "display_order")
    private Integer displayOrder;

    // 메모 기능 (옵션)
    @Column(name = "memo", length = 500)
    private String memo;

    @PrePersist
    protected void onCreate() {
        if (this.addedAt == null) {
            this.addedAt = LocalDateTime.now();
        }
    }

    @Override
    public String toString() {
        return "InterestStock{" +
                "interestId=" + interestId +
                ", userId=" + (user != null ? user.getId() : null) +
                ", stockId=" + (stock != null ? stock.getStockId() : null) +
                ", addedAt=" + addedAt +
                ", displayOrder=" + displayOrder +
                '}';
    }
}