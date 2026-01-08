package com.rookies4.finalProject.dto;

import com.rookies4.finalProject.domain.entity.PriceTarget;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PriceTargetDTO {

    // 목표가 설정 요청
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetTargetRequest {
        private String stockCode;
        private BigDecimal targetPrice;
    }

    // 목표가 응답
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceTargetResponse {
        private Long id;
        private String stockCode;
        private String stockName;
        private BigDecimal upperTarget;
        private BigDecimal lowerTarget;
        private Boolean isEnabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static PriceTargetResponse fromEntity(PriceTarget entity) {
            return PriceTargetResponse.builder()
                    .id(entity.getId())
                    .stockCode(entity.getStock().getStockCode())
                    .stockName(entity.getStock().getName())
                    .upperTarget(entity.getUpperTarget())
                    .lowerTarget(entity.getLowerTarget())
                    .isEnabled(entity.getIsEnabled())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .build();
        }
    }
}
