package com.rookies4.finalProject.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RealtimePriceDTO {

    private String stockCode;      // 종목코드(MKSC_SHRN_ISCD)
    private String tickTime;       // 주식 체결 시간(STCK_CNTG_HOUR)
    private BigDecimal price;      // 주식 현재가(STCK_PRPR)
    private BigDecimal diff;       // 전일 대비(PRDY_VRSS)
    private BigDecimal diffRate;   // 전일 대비율(PRDY_CTRT)
    private BigDecimal sellCount; // 매도 체결 건수(SELN_CNTG_CSNU)
    private BigDecimal buyCount; // 매수 체결 건수(SHNU_CNTG_CSNU)
    private BigDecimal askPrice; // 매도호가1(ASKP1)
    private BigDecimal bidPrice; // 매수호가1(BIDP1)
    private BigDecimal totalAskQuantity; // 총 매도호가 잔량(TOTAL_ASKP_RSQN)
    private BigDecimal totalBidQuantity; // 총 매수호가 잔량(TOTAL_BIDP_RSQN)
    private BigDecimal openPrice;  // 주식 시가(STCK_OPRC)
    private BigDecimal highPrice;  // 주식 최고가(STCK_HGPR)
    private BigDecimal lowPrice;   // 주식 최저가(STCK_LWPR)
    private BigDecimal volume;     // 누적 거래량(ACML_VOL)
    private BigDecimal tradingValue; // 누적 거래 대금(ACML_TR_PBMN)
}
