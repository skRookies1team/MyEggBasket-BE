package com.rookies4.finalProject.dto;

import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.Stock;
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
        @NotNull(message = "종목 ID(stockId)는 필수입니다.")
        private String ticker;
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
        private Portfolio portfolio;
        private Stock stock;
        private Integer quantity;
    }
}
