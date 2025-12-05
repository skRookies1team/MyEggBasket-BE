package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class KisForeignIndexDTO {

    // --- 1. 응답 전체 구조 (API 성공 여부 확인 및 output1/2 계층 구조 유지) ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KisForeignIndexResponse{
        @JsonProperty("rt_cd")
        private String rtCd;    // 응답 성공/실패 여부 (필수)
        @JsonProperty("msg_cd")
        private String msgCd;   // 응답코드 (필수)
        @JsonProperty("msg1")
        private String msg1;    // 응답메세지 (필수)

        @JsonProperty("output1")
        private ResponseOutput1 output1; // 요약 데이터 객체 (필수)

        @JsonProperty("output2")
        private List<ResponseOutput2> output2; // 상세 리스트 (사용하지 않더라도 구조는 유지)
    }


    // --- 2. output1: 필요한 필드만 정의 ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResponseOutput1 {
        // [필수 1] 해외 지수 현재가
        @JsonProperty("ovrs_nmix_prpr")
        private String ovrsNmixPrpr;

        // [필수 2] 해외 지수 전일 대비
        @JsonProperty("ovrs_nmix_prdy_vrss")
        private String ovrsNmixPrdyVrss;

        // [필수 3] 전일 대비율
        @JsonProperty("prdy_ctrt")
        private String prdyCtrt;
    }

    // --- 3. output2: 필요 없다면 비워둘 수 있음 (빈 클래스) ---
    // 리스트 구조를 유지하기 위해 클래스는 정의하되, 필드는 넣지 않거나 필요한 최소 필드만 정의합니다.
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseOutput2 {
        @JsonProperty("stck_cntg_hour")
        private String time;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KisForeignIndexRequest {
        private String indexCode;
    }
}