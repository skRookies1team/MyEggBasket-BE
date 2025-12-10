package com.rookies4.finalProject.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentPriceDTO {
    private String stockCode;        // 종목코드
    private Double currentPrice;     // 현재가
    private Double changeAmount;     // 전일대비
    private Double changeRate;       // 등락률
    private Double volume;           // 거래량
    private Double tradingValue;     // 거래대금
    private Double openPrice;        // 시가
    private Double highPrice;        // 고가
    private Double lowPrice;         // 저가
}