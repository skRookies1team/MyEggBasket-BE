package com.rookies4.finalProject.dto;

import com.rookies4.finalProject.domain.entity.Transaction;
import com.rookies4.finalProject.domain.enums.TransactionType;
import com.rookies4.finalProject.domain.enums.TriggerSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class TransactionDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long transactionId;

        // Stock 정보 매핑
        private Long stockId; // 종목 ID
        private String stockName; // 종목명
        private String ticker; // 종목코드

        // 거래 정보
        private TransactionType type; // BUY, SELL
        private String typeDescription; // "매수", "매도"

        private Integer quantity; // 수량
        private BigDecimal price; // 체결 단가
        private BigDecimal totalPrice; // 총 거래 금액

        // 주문 원천 정보
        private TriggerSource triggerSource; // MANUAL, AI
        private String triggerSourceName; // "수동 주문", "AI 주문" (UI 표시용)

        private LocalDateTime executedAt;

        public static Response fromEntity(Transaction transaction) {
            return Response.builder()
                    .transactionId(transaction.getTransactionId())
                    // Stock 엔티티가 null이 아닐 경우 정보 매핑
                    .stockId(transaction.getStock() != null ? transaction.getStock().getStockId() : null)
                    .stockName(transaction.getStock() != null ? transaction.getStock().getName() : null)
                    .ticker(transaction.getStock() != null ? transaction.getStock().getTicker() : null)

                    .type(transaction.getType())
                    .typeDescription(transaction.getType().getDescription()) // "매수" or "매도"

                    .quantity(transaction.getQuantity())
                    .price(transaction.getPrice())
                    // 총 금액 계산 (null safe 처리 권장)
                    .totalPrice(transaction.getPrice() != null && transaction.getQuantity() != null
                            ? transaction.getPrice().multiply(BigDecimal.valueOf(transaction.getQuantity()))
                            : BigDecimal.ZERO)

                    .triggerSource(transaction.getTriggerSource())
                    .triggerSourceName(transaction.getTriggerSource() != null
                            ? transaction.getTriggerSource().getName()
                            : null) // "수동 주문" or "AI 주문"

                    .executedAt(transaction.getExecutedAt())
                    .build();
        }
    }
}
