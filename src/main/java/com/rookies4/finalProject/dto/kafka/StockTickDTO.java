package com.rookies4.finalProject.dto.kafka; // [중요] 패키지 선언 확인

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
public class StockTickDTO {

    private String stockCode;    // 종목 코드
    private BigDecimal currentPrice; // 현재가
    private LocalDateTime timestamp; // 체결 시간
    private BigDecimal changeRate;   // 등락률
    private Long volume;             // 거래량
}