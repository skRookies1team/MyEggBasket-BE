package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentPriceDTO {
    private String stockCode;        // 종목코드
    private String stockName;           // 종목명
    private BigDecimal currentPrice;     // 현재가
    private Double changeAmount;     // 전일대비
    private Double changeRate;       // 등락률
    private Long volume;           // 거래량
    private Double tradingValue;     // 거래대금
    private Double openPrice;        // 시가
    private Double highPrice;        // 고가
    private Double lowPrice;         // 저가
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;     // 시세 갱신 시각
}