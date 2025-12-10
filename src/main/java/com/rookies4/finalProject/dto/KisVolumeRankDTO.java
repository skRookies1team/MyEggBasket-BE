package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class KisVolumeRankDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KisVolumeRankResponse {
        @JsonProperty("rt_cd")
        private String rtCd;    // 응답 성공/실패 여부

        @JsonProperty("msg_cd")
        private String msgCd;   // 응답코드

        @JsonProperty("msg1")
        private String msg1;    // 응답메세지

        @JsonProperty("output")
        private List<VolumeRankItem> output; // 거래량 순위 리스트
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VolumeRankItem {
        // 순위
        @JsonProperty("data_rank")
        private String rank;

        // 종목코드
        @JsonProperty("mksc_shrn_iscd")
        private String stockCode;

        // 종목명
        @JsonProperty("hts_kor_isnm")
        private String stockName;

        // 현재가
        @JsonProperty("stck_prpr")
        private String currentPrice;

        // 전일대비
        @JsonProperty("prdy_vrss")
        private String priceChange;

        // 등락률
        @JsonProperty("prdy_ctrt")
        private String changeRate;

        // 누적 거래량
        @JsonProperty("acml_vol")
        private String volume;

        // 전일 거래량
        @JsonProperty("prdy_vol")
        private String prevVolume;

        // 전일대비 거래량 비율
        @JsonProperty("prdy_vol_vrss_acml_vol_rate")
        private String volumeChangeRate;

        // 거래대금(백만)
        @JsonProperty("acml_tr_pbmn")
        private String tradingValue;

        // 거래량 회전율
        @JsonProperty("vol_inrt")
        private String volInrt;
    }
}