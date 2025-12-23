package com.rookies4.finalProject.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class BalanceDTO {

    // output2
    @Data
    public static class Summary {

        // 예수금총금액 (KIS: dnca_tot_amt)
        private BigDecimal totalCashAmount;

        // D+1 예수금 (KIS: nxdy_excc_amt)
        private BigDecimal d1CashAmount;

        // D+2 예수금 (KIS: prvs_rcdl_excc_amt)
        private BigDecimal d2CashAmount;

        // 총평가금액 (KIS: tot_evlu_amt)
        private BigDecimal totalEvaluationAmount;

        // 평가손익합계금액 (KIS: evlu_pfls_smtl_amt)
        private BigDecimal totalProfitLossAmount;

        // 매입금액합계금액 (KIS: pchs_amt_smtl_amt)
        private BigDecimal totalPurchaseAmount;

        // 순자산금액 (KIS: nass_amt)
        private BigDecimal netAssetAmount;

        // 자산 수익률(필요시 계산해서 세팅, KIS: asst_icdc_erng_rt 기반 or 직접 계산)
        private BigDecimal profitRate;
    }

    // output1
    @Data
    public static class Holding {

        // 종목코드 (KIS: pdno)
        private String stockCode;

        // 종목명 (KIS: prdt_name)
        private String stockName;

        // 보유수량 (KIS: hldg_qty)
        private Integer quantity;

        // 주문가능수량 (KIS: ord_psbl_qty)
        private Integer orderableQuantity;

        // 매입평균가격 (KIS: pchs_avg_pric)
        private BigDecimal avgPrice;

        // 현재가 (KIS: prpr)
        private BigDecimal currentPrice;

        // 평가금액 (KIS: evlu_amt)
        private BigDecimal evaluationAmount;

        // 평가손익금액 (KIS: evlu_pfls_amt)
        private BigDecimal profitLossAmount;

        // 평가손익율 (KIS: evlu_pfls_rt, % 값)
        private BigDecimal profitLossRate;
    }

    @Data
    public static class Response {

        // 계좌 요약 정보
        private Summary summary;

        // 보유 종목 리스트
        private List<Holding> holdings;
    }
}
