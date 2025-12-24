package com.rookies4.finalProject.dto;

import com.rookies4.finalProject.domain.entity.StockSubscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class StockSubscriptionDTO {

    /**
     * 구독 요청 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubscriptionRequest {
        private String stockCode;
    }

    /**
     * 구독 응답 DTO
     */
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

        public static SubscriptionResponse fromEntity(StockSubscription subscription) {
            return SubscriptionResponse.builder()
                    .id(subscription.getId())
                    .userId(subscription.getUser().getId())
                    .stockCode(subscription.getStock().getStockCode())
                    .stockName(subscription.getStockName())
                    .subscribedAt(subscription.getSubscribedAt())
                    .build();
        }
    }
}
