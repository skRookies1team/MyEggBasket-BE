package com.rookies4.finalProject.dto;

import com.rookies4.finalProject.domain.entity.Holding;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public class HoldingDTO {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HoldingRequest {
        @NotNull(message = "종목 코드(stockCode)는 필수입니다.")
        private String stockCode;
        @NotNull(message = "보유 수량(quantity)은 필수입니다.")
        private Integer quantity;
        private BigDecimal avgPrice;
        private Float currentWeight;
        private Float targetWeight;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HoldingResponse{
        private Long holdingId;
        private Long portfolioId; // Portfolio 엔티티 대신 portfolioId만 포함
        private StockDTO.StockResponse stock; // Stock 엔티티 대신 StockDTO 사용
        private Integer quantity;
        private BigDecimal avgPrice;
        private Float currentWeight;
        private Float targetWeight;

        public static HoldingDTO.HoldingResponse fromEntity(Holding holding) {
            return HoldingResponse.builder()
                    .holdingId(holding.getHoldingId())
                    .portfolioId(holding.getPortfolio() != null ? holding.getPortfolio().getPortfolioId() : null)
                    .stock(holding.getStock() != null ? StockDTO.StockResponse.fromEntity(holding.getStock()) : null)
                    .quantity(holding.getQuantity())
                    .avgPrice(holding.getAvgPrice())
                    .currentWeight(holding.getCurrentWeight())
                    .targetWeight(holding.getTargetWeight())
                    .build();
        }
    }
}
