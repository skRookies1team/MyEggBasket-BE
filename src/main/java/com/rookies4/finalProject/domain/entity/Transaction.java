package com.rookies4.finalProject.domain.entity;

import com.rookies4.finalProject.domain.enums.TransactionStatus;
import com.rookies4.finalProject.domain.enums.TransactionType;
import com.rookies4.finalProject.domain.enums.TriggerSource;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 10, nullable = false)
    private TransactionType type; // BUY, SELL

    @Column(name = "quantity")
    private Integer quantity; // 거래 수량

    @Column(name = "price", precision = 20, scale = 2)
    private BigDecimal price; // 체결 가격

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TransactionStatus status;

//    @Column(name = "fee", precision = 10, scale = 2)
//    private BigDecimal fee; // 수수료
//
//    @Column(name = "tax", precision = 10, scale = 2)
//    private BigDecimal tax; // 세금

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", length = 50)
    private TriggerSource triggerSource; // 주문 원천

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @PrePersist
    protected void onCreate() {
        if (this.executedAt == null) {
            this.executedAt = LocalDateTime.now();
        }

        // status 가 설정되지 않았다면 기본적으로 'PENDING' 으로 설정
        if (this.status == null) {
            this.status = TransactionStatus.COMPLETED;
        }
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId=" + transactionId +
                ", userId=" + (user != null ? user.getId() : null) +
                ", stockId=" + (stock != null ? stock.getStockId() : null) +
                ", type='" + type + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", executedAt=" + executedAt +
                '}';
    }
}