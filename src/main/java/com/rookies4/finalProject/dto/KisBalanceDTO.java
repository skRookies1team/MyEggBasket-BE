package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisBalanceDTO {

    // 성공 실패 여부
    @JsonProperty("rt_cd")
    private String rtCd;

    // 응답코드
    @JsonProperty("msg_cd")
    private String msgCd;

    // 응답메세지
    @JsonProperty("msg1")
    private String msg1;

    // 연속조회검색조건100
    @JsonProperty("ctx_area_fk100")
    private String ctxAreaFk100;

    // 연속조회키100
    @JsonProperty("ctx_area_nk100")
    private String ctxAreaNk100;

    // 응답상세1
    @JsonProperty("output1")
    private List<KisBalanceDetail> output1;

    // 응답상세2
    @JsonProperty("output2")
    private List<OutputSummary> output2;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KisBalanceDetail {

        // 상품번호
        @JsonProperty("pdno")
        private String pdno;

        // 상품명
        @JsonProperty("prdt_name")
        private String prdtName;

        // 매매구분명
        @JsonProperty("trad_dvsn_name")
        private String tradDvsnName;

        // 전일매수수량
        @JsonProperty("bfdy_buy_qty")
        private String bfdyBuyQty;

        // 전일매도수량
        @JsonProperty("bfdy_sll_qty")
        private String bfdySllQty;

        // 금일매수수량
        @JsonProperty("thdt_buyqty")
        private String thdtBuyqty;

        // 금일매도수량
        @JsonProperty("thdt_sll_qty")
        private String thdtSllQty;

        // 보유수량
        @JsonProperty("hldg_qty")
        private String hldgQty;

        // 주문가능수량
        @JsonProperty("ord_psbl_qty")
        private String ordPsblQty;

        // 매입평균가격
        @JsonProperty("pchs_avg_pric")
        private String pchsAvgPric;

        // 매입금액
        @JsonProperty("pchs_amt")
        private String pchsAmt;

        // 현재가
        @JsonProperty("prpr")
        private String prpr;

        // 평가금액
        @JsonProperty("evlu_amt")
        private String evluAmt;

        // 평가손익금액
        @JsonProperty("evlu_pfls_amt")
        private String evluPflsAmt;

        // 평가손익율
        @JsonProperty("evlu_pfls_rt")
        private String evluPflsRt;

        // 평가수익율
        @JsonProperty("evlu_erng_rt")
        private String evluErngRt;

        // 대출일자
        @JsonProperty("loan_dt")
        private String loanDt;

        // 대출금액
        @JsonProperty("loan_amt")
        private String loanAmt;

        // 대주매각대금
        @JsonProperty("stln_slng_chgs")
        private String stlnSlngChgs;

        // 만기일자
        @JsonProperty("expd_dt")
        private String expdDt;

        // 등락율
        @JsonProperty("fltt_rt")
        private String flttRt;

        // 전일대비증감
        @JsonProperty("bfdy_cprs_icdc")
        private String bfdyCprsIcdc;

        // 종목증거금율명
        @JsonProperty("item_mgna_rt_name")
        private String itemMgnaRtName;

        // 보증금율명
        @JsonProperty("grta_rt_name")
        private String grtaRtName;

        // 대용가격
        @JsonProperty("sbst_pric")
        private String sbstPric;

        // 주식대출단가
        @JsonProperty("stck_loan_unpr")
        private String stckLoanUnpr;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutputSummary {

        // 예수금총금액
        @JsonProperty("dnca_tot_amt")
        private String dncaTotAmt;

        // 익일정산금액 (D+1 예수금)
        @JsonProperty("nxdy_excc_amt")
        private String nxdyExccAmt;

        // 가수도정산금액 (D+2 예수금)
        @JsonProperty("prvs_rcdl_excc_amt")
        private String prvsRcdlExccAmt;

        // CMA평가금액
        @JsonProperty("cma_evlu_amt")
        private String cmaEvluAmt;

        // 전일매수금액
        @JsonProperty("bfdy_buy_amt")
        private String bfdyBuyAmt;

        // 금일매수금액
        @JsonProperty("thdt_buy_amt")
        private String thdtBuyAmt;

        // 익일자동상환금액
        @JsonProperty("nxdy_auto_rdpt_amt")
        private String nxdyAutoRdptAmt;

        // 전일매도금액
        @JsonProperty("bfdy_sll_amt")
        private String bfdySllAmt;

        // 금일매도금액
        @JsonProperty("thdt_sll_amt")
        private String thdtSllAmt;

        // D+2자동상환금액
        @JsonProperty("d2_auto_rdpt_amt")
        private String d2AutoRdptAmt;

        // 전일제비용금액
        @JsonProperty("bfdy_tlex_amt")
        private String bfdyTlexAmt;

        // 금일제비용금액
        @JsonProperty("thdt_tlex_amt")
        private String thdtTlexAmt;

        // 총대출금액
        @JsonProperty("tot_loan_amt")
        private String totLoanAmt;

        // 유가평가금액
        @JsonProperty("scts_evlu_amt")
        private String sctsEvluAmt;

        // 총평가금액
        @JsonProperty("tot_evlu_amt")
        private String totEvluAmt;

        // 순자산금액
        @JsonProperty("nass_amt")
        private String nassAmt;

        // 융자금자동상환여부
        @JsonProperty("fncg_gld_auto_rdpt_yn")
        private String fncgGldAutoRdptYn;

        // 매입금액합계금액
        @JsonProperty("pchs_amt_smtl_amt")
        private String pchsAmtSmtlAmt;

        // 평가금액합계금액
        @JsonProperty("evlu_amt_smtl_amt")
        private String evluAmtSmtlAmt;

        // 평가손익합계금액
        @JsonProperty("evlu_pfls_smtl_amt")
        private String evluPflsSmtlAmt;

        // 총대주매각대금
        @JsonProperty("tot_stln_slng_chgs")
        private String totStlnSlngChgs;

        // 전일총자산평가금액
        @JsonProperty("bfdy_tot_asst_evlu_amt")
        private String bfdyTotAsstEvluAmt;

        // 자산증감액
        @JsonProperty("asst_icdc_amt")
        private String asstIcdcAmt;

        // 자산증감수익율
        @JsonProperty("asst_icdc_erng_rt")
        private String asstIcdcErngRt;
    }
}