package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rookies4.finalProject.domain.entity.*;
import com.rookies4.finalProject.domain.enums.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public class PortfolioDTO {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PortfolioRequest{

        @NotBlank(message= "포트폴리오 이름을 정해주세요")
        private String name;
        private BigDecimal totalAsset = BigDecimal.ZERO;//default =0 설정
        private BigDecimal cashBalance = BigDecimal.ZERO; //default =0 설정
        private RiskLevel riskLevel;

    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PortfolioResponse{
        private Long portfolioId;
        private User user;
        private String name;
        private BigDecimal totalAsset;
        private BigDecimal cashBalance;
        private RiskLevel riskLevel;
        private List<Holding> holdings;
        private List<AIRecommendation> recommendations;
        private List<HistoryReports> historyReports;

        public static PortfolioDTO.PortfolioResponse fromEntity(Portfolio portfolio) {
            return PortfolioResponse.builder()
                    .portfolioId(portfolio.getPortfolioId())
                    .user(portfolio.getUser())
                    .name(portfolio.getName())
                    .totalAsset(portfolio.getTotalAsset())
                    .cashBalance(portfolio.getCashBalance())
                    .riskLevel(portfolio.getRiskLevel())
                    .holdings(portfolio.getHoldings())
                    .recommendations(portfolio.getRecommendations())
                    .historyReports(portfolio.getHistoryReports())
                    .build();
        }
    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PortfolioHoldingResponse{
        private Long portfolioId;
        private List<Holding> holdings;

        public static PortfolioDTO.PortfolioHoldingResponse fromEntity(Portfolio portfolio) {
            return PortfolioHoldingResponse.builder()
                    .portfolioId(portfolio.getPortfolioId())
                    .holdings(portfolio.getHoldings())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HoldingRequest {
        @NotNull(message = "종목 ID(stockId)는 필수입니다.")
        private Long stockId;
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
