package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisTransactionDto {

    /** 성공/실패 여부 (rt_cd) */
    @JsonProperty("rt_cd")
    private String rtCd;

    /** 응답 코드 (msg_cd) */
    @JsonProperty("msg_cd")
    private String msgCd;

    /** 응답 메시지 (msg1) */
    @JsonProperty("msg1")
    private String msg1;

    /** 주문/체결 상세 목록 (output1) */
    @JsonProperty("output1")
    private List<KisOrderDetail> output1;

    /** 집계 정보 (output2) */
    @JsonProperty("output2")
    private OutputSummary output2;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KisOrderDetail {

        /** 주문번호 (odno) */
        @JsonProperty("odno")
        private String orderNo;

        /** 상품번호 (pdno) */
        @JsonProperty("pdno")
        private String stockCode;

        /** 상품명 (prdt_name) */
        @JsonProperty("prdt_name")
        private String stockName;

        /** 매도/매수 구분 코드 (sll_buy_dvsn_cd, 01=매도, 02=매수) */
        @JsonProperty("sll_buy_dvsn_cd")
        private String buySellCode;

        /** 주문수량 (ord_qty) */
        @JsonProperty("ord_qty")
        private String orderQty;

        /** 총체결수량 (tot_ccld_qty) */
        @JsonProperty("tot_ccld_qty")
        private String filledQty;

        @JsonProperty("cnc_cfrm_qty")
        private String cancelQty;

        /** 평균가 (avg_prvs) */
        @JsonProperty("avg_prvs")
        private String avgPrice;

        /** 주문일자 (ord_dt, YYYYMMDD) */
        @JsonProperty("ord_dt")
        private String orderDate;

        /** 주문시각 (ord_tmd, HHMMSS) */
        @JsonProperty("ord_tmd")
        private String orderTime;

        @JsonProperty("cncl_yn")
        private String cancelYn;  // 취소여부 (Y/N)

        @JsonProperty("tot_ccld_amt")
        private String totalFilledAmount; // 총체결금액

        @JsonProperty("rmn_qty")
        private String remainQty; // 잔여수량

        @JsonProperty("rjct_qty")
        private String rejectQty; // 거부수량

        @JsonProperty("excg_dvsn_cd")
        private String exchangeDivisionCode; // 거래소구분코드

        @JsonProperty("excg_id_dvsn_Cd")
        private String exchangeIdDivisionCode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutputSummary {

        @JsonProperty("tot_ord_qty")
        private String totalOrderQty;

        @JsonProperty("tot_ccld_qty")
        private String totalFilledQty;

        @JsonProperty("tot_ccld_amt")
        private String totalFilledAmount;

        @JsonProperty("prsm_tlex_smtl")
        private String estimatedTotalFee;

        @JsonProperty("pchs_avg_pric")
        private String purchaseAveragePrice;
    }
}