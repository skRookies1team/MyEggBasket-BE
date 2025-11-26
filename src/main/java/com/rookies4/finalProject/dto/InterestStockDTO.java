package com.rookies4.finalProject.dto;

import com.rookies4.finalProject.domain.entity.InterestStock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class InterestStockDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InterestStockRequest{
        @NotNull(message = "관심 종목으로 등록할 주식을 선택해 주세요.")
        private Long stockId;
        private String memo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InterestStockResponse{
        private Long interestId;
        private Long userId;
        private StockDTO.StockResponse stock;
        private String memo;
        private LocalDateTime addedAt;

        public static InterestStockDTO.InterestStockResponse fromEntity(InterestStock interestStock){
            return InterestStockResponse.builder()
                    .interestId(interestStock.getInterestId())
                    .userId(interestStock.getUser() != null ? interestStock.getUser().getId() : null)
                    .stock(interestStock.getStock()!= null ? StockDTO.StockResponse.fromEntity(interestStock.getStock()) : null)
                    .memo(interestStock.getMemo())
                    .addedAt(interestStock.getAddedAt())
                    .build();
        }
    }
}
