package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rookies4.finalProject.domain.entity.AIRecommendation;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.enums.RecommendationAction;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AIRecommendationDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecommendationCreateRequest {

        // [수정] userId 추가, portfolioId는 선택사항으로 변경 (둘 중 하나는 필수여야 함을 로직으로 처리)
        private Long userId;

        // @NotNull(message = "portfolioId는 필수입니다.") -> 제거 (userId로 대체 가능하므로)
        private Long portfolioId;

        @NotBlank(message = "종목코드는 필수입니다.")
        private String stockCode;

        @NotNull(message = "aiScore는 필수입니다.")
        @DecimalMin(value = "0.0", message = "aiScore는 0 이상이어야 합니다.")
        @DecimalMax(value = "100.0", message = "aiScore는 100 이하여야 합니다.")
        private Float aiScore;

        @NotNull(message = "actionType은 필수입니다.")
        private RecommendationAction actionType;

        @NotNull(message = "currentHolding은 필수입니다.")
        private BigDecimal currentHolding;

        @NotNull(message = "targetHolding은 필수입니다.")
        private BigDecimal targetHolding;

        @NotNull(message = "targetHoldingPercentage는 필수입니다.")
        private Float targetHoldingPercentage;

        @NotNull(message = "adjustmentAmount는 필수입니다.")
        private BigDecimal adjustmentAmount;

        @Size(max = 500, message = "reasonSummary는 500자를 초과할 수 없습니다.")
        private String reasonSummary;

        @Size(max = 500, message = "riskWarning은 500자를 초과할 수 없습니다.")
        private String riskWarning;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecommendationResponse {
        private Long recommendationId;
        private Long portfolioId;
        private String stockCode;
        private String stockName;

        private Float aiScore; // AI 점수 (0~100)
        private RecommendationAction actionType; // BUY, SELL, HOLD

        private BigDecimal currentHolding; // 현재 보유
        private String targetHoldingDisplay; // "~원 (~%)" 형식
        private BigDecimal adjustmentAmount; // 조절 금액 (+/-)

        private String reasonSummary; // 이유
        private String riskWarning;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;

        public static RecommendationResponse fromEntity(AIRecommendation recommendation) {
            Stock stock = recommendation.getStock();
            String targetDisplay = formatTargetHolding(
                    recommendation.getTargetHolding(),
                    recommendation.getTargetHoldingPercentage()
            );

            return RecommendationResponse.builder()
                    .recommendationId(recommendation.getRecoId())
                    .portfolioId(recommendation.getPortfolio() != null ? recommendation.getPortfolio().getPortfolioId() : null)
                    .stockCode(stock != null ? stock.getStockCode() : null)
                    .stockName(stock != null ? stock.getName() : null)
                    .aiScore(recommendation.getAiScore())
                    .actionType(recommendation.getActionType())
                    .currentHolding(recommendation.getCurrentHolding())
                    .targetHoldingDisplay(targetDisplay)
                    .adjustmentAmount(recommendation.getAdjustmentAmount())
                    .reasonSummary(recommendation.getReasonSummary())
                    .riskWarning(recommendation.getRiskWarning())
                    .createdAt(recommendation.getCreatedAt())
                    .build();
        }

        private static String formatTargetHolding(BigDecimal amount, Float percentage) {
            if (amount == null) {
                return "0원 (0%)";
            }
            String percentStr = percentage != null ? String.format("%.1f", percentage) : "0.0";
            return String.format("%,d원 (%s%%)", amount.toBigInteger(), percentStr);
        }
    }
}
