package com.rookies4.finalProject.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// stock-ticks 토픽에서 수신하는 실시간 체결 데이터 DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTickDTO {

    private String stockCode; //종목 코드
    private BigDecimal currentPrice; //현재가
    private LocalDateTime timestamp; //체결 시간
    private BigDecimal changeRate; //전일 대비 등락률
    private Long volume; //거래량
}
