package com.rookies4.finalProject.dto;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class StockDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockRequest{
        private String stockCode; // 종목코드 (예: 005930)
        private String name; // 종목명 (예: 삼성전자)
        private String marketType; // KOSPI, KOSDAQ
        private String sector; // 섹터 (반도체, 2차전지 등)
        private String industryCode; // 산업분류코드
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockResponse{
        private String stockCode; // 종목코드 (예: 005930)
        private String name; // 종목명 (예: 삼성전자)
        private String marketType; // KOSPI, KOSDAQ
        private String sector; // 섹터 (반도체, 2차전지 등)
        private String industryCode; // 산업분류코드

        // Entity를 DTO로 변환하는 생성자
        public static StockDTO.StockResponse fromEntity(Stock stock) {
            return StockResponse.builder()
                    .stockCode(stock.getStockCode())
                    .name(stock.getName())
                    .marketType(stock.getMarketType())
                    .sector(stock.getSector())
                    .industryCode(stock.getIndustryCode())
                    .build();
        }
    }

}

