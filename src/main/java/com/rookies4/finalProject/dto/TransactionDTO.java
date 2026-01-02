package com.rookies4.finalProject.dto;

import com.rookies4.finalProject.domain.entity.Transaction;
import com.rookies4.finalProject.domain.enums.TransactionStatus;
import com.rookies4.finalProject.domain.enums.TransactionType;
import com.rookies4.finalProject.domain.enums.TriggerSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

        private List<Long> portfolioIds; // 포트폴리오 ID 리스트
        private List<String> portfolioNames; // 포트폴리오 이름 리스트

        // Stock 정보 매핑
        private String stockCode; // 종목코드
        private String stockName; // 종목명

        // 거래 정보
        private TransactionType type; // BUY, SELL
        private String typeDescription; // "매수", "매도"

        private TransactionStatus status;
        private String statusDescription; // "미체결", "체결"

        private Integer quantity; // 수량
        private BigDecimal price; // 체결 단가
        private BigDecimal totalPrice; // 총 거래 금액

        // 주문 원천 정보
        private TriggerSource triggerSource; // MANUAL, AI
        private String triggerSourceName; // "수동 주문", "AI 주문" (UI 표시용)

        private LocalDateTime executedAt;

        public static Response fromEntity(Transaction transaction) {
            List<Long> portfolioIds = null;
            List<String> portfolioNames = null;
            
            if (transaction.getPortfolios() != null && !transaction.getPortfolios().isEmpty()) {
                portfolioIds = transaction.getPortfolios().stream()
                        .map(p -> p.getPortfolioId())
                        .toList();
                portfolioNames = transaction.getPortfolios().stream()
                        .map(p -> p.getName())
                        .toList();
            }
            
            return Response.builder()
                    .transactionId(transaction.getTransactionId())
                    .portfolioIds(portfolioIds)
                    .portfolioNames(portfolioNames)
                    .stockCode(transaction.getStock() != null ? transaction.getStock().getStockCode() : null)
                    .stockName(transaction.getStock() != null ? transaction.getStock().getName() : null)

                    .type(transaction.getType())
                    .typeDescription(transaction.getType().getDescription())

                    .status(transaction.getStatus())
                    .statusDescription(transaction.getStatus() != null ? transaction.getStatus().getDescription() : null)

                    .quantity(transaction.getQuantity())
                    .price(transaction.getPrice())
                    .totalPrice(transaction.getPrice() != null && transaction.getQuantity() != null
                            ? transaction.getPrice().multiply(BigDecimal.valueOf(transaction.getQuantity()))
                            : BigDecimal.ZERO)

                    .triggerSource(transaction.getTriggerSource())
                    .triggerSourceName(transaction.getTriggerSource() != null ? transaction.getTriggerSource().getName() : null)

                    .executedAt(transaction.getExecutedAt())
                    .build();
        }
    }
}