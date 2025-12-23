package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class KisInvestorTrendDTO {

    /**
     * 프론트엔드 응답용 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestorTrendResponse {
        private String stockCode;
        private String stockName;
        private Long closePrice;      // 주식 종가
        private Long changeAmount;    // 전일 대비
        private String changeSign;    // 전일 대비 부호
        private List<InvestorInfo> investors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestorInfo {
        private String type;          // 개인, 외국인, 기관
        private Long netBuyQty;       // 순매수 수량
        private Long netBuyAmount;    // 순매수 거래대금
    }

    /**
     * KIS API 응답 매핑용 DTO (내부 사용)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KisApiResponse {
        @JsonProperty("rt_cd")
        private String rtCd;

        @JsonProperty("msg1")
        private String msg1;

        @JsonProperty("output")
        private List<KisOutput> output;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KisOutput {
        // 추가된 필드 3개
        @JsonProperty("stck_clpr")
        private String closePrice;
        
        @JsonProperty("prdy_vrss")
        private String changeAmount;
        
        @JsonProperty("prdy_vrss_sign")
        private String changeSign;

        // 개인 순매수 수량 & 거래대금
        @JsonProperty("prsn_ntby_qty")
        private String personalNetBuyQty;
        @JsonProperty("prsn_ntby_tr_pbmn")
        private String personalNetBuyAmount;
        
        // 외국인 순매수 수량 & 거래대금
        @JsonProperty("frgn_ntby_qty")
        private String foreignerNetBuyQty;
        @JsonProperty("frgn_ntby_tr_pbmn")
        private String foreignerNetBuyAmount;
        
        // 기관계 순매수 수량 & 거래대금
        @JsonProperty("orgn_ntby_qty")
        private String institutionNetBuyQty;
        @JsonProperty("orgn_ntby_tr_pbmn")
        private String institutionNetBuyAmount;
        
        // 종목명
        @JsonProperty("hts_kor_isnm")
        private String stockName;
    }
}