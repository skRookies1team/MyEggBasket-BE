package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rookies4.finalProject.domain.entity.*;
import com.rookies4.finalProject.domain.enums.RiskLevel;
import jakarta.validation.constraints.NotBlank;
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
        private List<String> stockCodes; // 포트폴리오에 포함할 종목 코드 리스트

    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PortfolioResponse{
        private Long portfolioId;
        private Long userId; // User 엔티티 대신 userId만 포함
        private String name;
        private BigDecimal totalAsset;
        private BigDecimal cashBalance;
        private RiskLevel riskLevel;
        
        // Holdings를 DTO로 변환하여 무한 참조 방지
        private List<HoldingDTO.HoldingResponse> holdings;
        
        // Recommendations와 HistoryReports는 null로 설정 (필요시 별도 엔드포인트에서 조회)
        // 무한 참조 방지를 위해 엔티티 리스트 대신 null 또는 간단한 정보만 포함
        private List<AIRecommendation> recommendations = null;
        private List<HistoryReport> historyReports = null;

        public static PortfolioDTO.PortfolioResponse fromEntity(Portfolio portfolio) {
            // Holdings를 DTO로 변환 (quantity > 0인 것만 포함)
            List<HoldingDTO.HoldingResponse> holdingsDto = null;
            if (portfolio.getHoldings() != null && !portfolio.getHoldings().isEmpty()) {
                holdingsDto = portfolio.getHoldings().stream()
                        .filter(h -> h.getQuantity() != null && h.getQuantity() > 0)
                        .map(HoldingDTO.HoldingResponse::fromEntity)
                        .collect(java.util.stream.Collectors.toList());
            }
            
            return PortfolioResponse.builder()
                    .portfolioId(portfolio.getPortfolioId())
                    .userId(portfolio.getUser() != null ? portfolio.getUser().getId() : null)
                    .name(portfolio.getName())
                    .totalAsset(portfolio.getTotalAsset())
                    .cashBalance(portfolio.getCashBalance())
                    .riskLevel(portfolio.getRiskLevel())
                    .holdings(holdingsDto)
                    .recommendations(null) // 무한 참조 방지를 위해 null로 설정
                    .historyReports(null) // 무한 참조 방지를 위해 null로 설정
                    .build();
        }
    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PortfolioHoldingResponse{
        private Long portfolioId;
        private List<HoldingDTO.HoldingResponse> holdings;

        public static PortfolioDTO.PortfolioHoldingResponse fromEntity(Portfolio portfolio) {
            // quantity > 0인 holding만 포함
            List<HoldingDTO.HoldingResponse> holdingsDto = null;
            if (portfolio.getHoldings() != null && !portfolio.getHoldings().isEmpty()) {
                holdingsDto = portfolio.getHoldings().stream()
                        .filter(h -> h.getQuantity() != null && h.getQuantity() > 0)
                        .map(HoldingDTO.HoldingResponse::fromEntity)
                        .collect(java.util.stream.Collectors.toList());
            }
            return PortfolioHoldingResponse.builder()
                    .portfolioId(portfolio.getPortfolioId())
                    .holdings(holdingsDto)
                    .build();
        }
    }
}
