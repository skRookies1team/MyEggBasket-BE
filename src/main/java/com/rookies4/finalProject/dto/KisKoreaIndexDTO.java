package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class KisKoreaIndexDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KisKoreaIndexResponse {
        @JsonProperty("rt_cd")
        private String rtCd;    // 응답 성공/실패 여부 (필수)
        @JsonProperty("msg_cd")
        private String msgCd;   // 응답코드 (필수)
        @JsonProperty("msg1")
        private String msg1;    // 응답메세지 (필수)
        @JsonProperty("output")
        private KisKoreaIndexDTO.ResponseOutput output;

    }

    // --- 2. output: 필요한 필드만 정의 ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResponseOutput {
        @JsonProperty("stck_cntg_hour")
        private String stockHour;

        @JsonProperty("bstp_nmix_prpr")
        private String presentPrice;

        @JsonProperty("bstp_nmix_prdy_vrss")
        private String priceCompated;

        @JsonProperty("bstp_nmix_prdy_ctrt")
        private String rateCompared;

        @JsonProperty("acml_vol")
        private String tradingVolume;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KisKoreaIndexRequest{
        private String indexCode;
    }
}

