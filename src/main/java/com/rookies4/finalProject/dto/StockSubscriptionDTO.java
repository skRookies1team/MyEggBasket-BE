package com.rookies4.finalProject.dto;

import com.rookies4.finalProject.domain.entity.StockSubscription;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class StockSubscriptionDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubscriptionRequest {
        @NotNull(message = "종목 코드는 필수입니다.")
        private String stockCode;

        /**
         * 구독 타입: "VIEW" (단순 조회), "INTEREST" (관심 종목)
         * 프론트엔드에서 이 값을 보내줘야 서비스에서 분기 처리가 가능합니다.
         */
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubscriptionResponse {
        private Long id;
        private Long userId;
        private String stockCode;
        private String stockName;
        private LocalDateTime subscribedAt;

        public static SubscriptionResponse fromEntity(StockSubscription entity) {
            return SubscriptionResponse.builder()
                    .id(entity.getId())
                    .userId(entity.getUser().getId())
                    .stockCode(entity.getStock().getStockCode())
                    .stockName(entity.getStockName())
                    .subscribedAt(entity.getSubscribedAt())
                    .build();
        }
    }
}