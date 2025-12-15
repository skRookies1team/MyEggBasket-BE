package com.rookies4.finalProject.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RealtimePriceDTO {

    private String stockCode;               // 종목코드(MKSC_SHRN_ISCD)
    private String tickTime;                // 주식 체결 시간(STCK_CNTG_HOUR)

    private long price;               // 주식 현재가(STCK_PRPR)
    private long diff;                // 전일 대비(PRDY_VRSS)
    private double diffRate;            // 전일 대비율(PRDY_CTRT)

    private long sellCount;           // 매도 체결 건수(SELN_CNTG_CSNU)
    private long buyCount;            // 매수 체결 건수(SHNU_CNTG_CSNU)

    private long askPrice;            // 매도호가1(ASKP1)
    private long bidPrice;            // 매수호가1(BIDP1)
    private long totalAskQuantity;    // 총 매도호가 잔량(TOTAL_ASKP_RSQN)
    private long totalBidQuantity;    // 총 매수호가 잔량(TOTAL_BIDP_RSQN)

    private long openPrice;           // 주식 시가(STCK_OPRC)
    private long highPrice;           // 주식 최고가(STCK_HGPR)
    private long lowPrice;            // 주식 최저가(STCK_LWPR)

    private long volume;              // 누적 거래량(ACML_VOL)
    private long tradingValue;        // 누적 거래 대금(ACML_TR_PBMN)
}
