package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class KisIndexDTO {

    /**
     * KIS 지수 API 응답 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IndexResponse {
        @JsonProperty("rt_cd")
        private String rtCd;

        @JsonProperty("msg_cd")
        private String msgCd;

        @JsonProperty("msg1")
        private String msg1;

        @JsonProperty("output")
        private IndexOutput output;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexOutput {
        // 국내 지수 필드
        @JsonProperty("hts_kor_isnm")
        private String indexName; // 지수명

        @JsonProperty("bstp_nmix_prpr")
        private String currentIndex; // 현재 지수

        @JsonProperty("bstp_nmix_prdy_vrss")
        private String changeAmount; // 전일 대비

        @JsonProperty("prdy_vrss_sign")
        private String changeSign; // 전일 대비 부호

        @JsonProperty("bstp_nmix_prdy_ctrt")
        private String changeRate; // 등락률

        // 해외 지수 필드 (필드명이 다를 수 있으므로, 국내 지수 필드와 유사하게 매핑)
        @JsonProperty("last")
        private String foreignIndex; // 해외 지수 현재가

        @JsonProperty("diff")
        private String foreignChangeAmount; // 해외 지수 전일 대비

        @JsonProperty("rate")
        private String foreignChangeRate; // 해외 지수 등락률
    }
}