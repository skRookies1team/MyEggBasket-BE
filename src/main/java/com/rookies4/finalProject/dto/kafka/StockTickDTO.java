package com.rookies4.finalProject.dto.kafka;

import java.math.BigDecimal;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTickDTO {

    private String type; // 메시지 타입 ("STOCK_TICK")

    private String stockCode; // 종목 코드 (MKSC_SHRN_ISCD)
    private String tickTime; // 주식 체결 시간 (STCK_CNTG_HOUR)

    private BigDecimal currentPrice; // 주식 현재가 (STCK_PRPR)
    private BigDecimal diff; // 전일 대비 (PRDY_VRSS)
    private BigDecimal diffRate; // 전일 대비율 (PRDY_CTRT)

    private BigDecimal volume; // 누적 거래량 (ACML_VOL)
    private BigDecimal tradingValue; // 누적 거래 대금 (ACML_TR_PBMN)
}