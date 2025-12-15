package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * KIS 지수 조회 API 응답 DTO
 * 
 * <p>이 DTO는 국내 지수와 해외 지수 조회 API의 공통 응답 구조를 처리합니다.
 * 
 * <p>사용 API:
 * <ul>
 *   <li>국내 지수: /uapi/domestic-stock/v1/quotations/inquire-index-price (tr_id: FHPUP01700000)</li>
 *   <li>해외 지수: /uapi/overseas-price/v1/quotations/price (tr_id: HHDFS00000300)</li>
 * </ul>
 * 
 * <p>IndexOutput은 국내 지수 필드와 해외 지수 필드를 모두 포함하여
 * 각 API가 반환하는 필드에 유연하게 대응합니다.
 */
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