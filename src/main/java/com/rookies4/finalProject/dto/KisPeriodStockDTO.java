package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class KisPeriodStockDTO {

    /**
     * 프론트엔드 응답용 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartResponse {
        private String stockCode;
        private String period; // day, week, month, year
        private List<ChartData> data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartData {
        private String time;   // 날짜 (YYYY-MM-DD)
        private Long price;    // 종가 (Close Price)
        private Long open;     // 시가 (Open Price)
        private Long high;     // 고가 (High Price)
        private Long low;      // 저가 (Low Price)
        private Long volume;   // 거래량
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

        @JsonProperty("output1")
        private KisOutput1 output1; // 종목 정보 (현재가 등)

        @JsonProperty("output2")
        private List<KisOutput2> output2; // 일별 데이터 리스트
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KisOutput1 {
        @JsonProperty("hts_kor_isnm")
        private String stockName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KisOutput2 {
        @JsonProperty("stck_bsop_date")
        private String date; // 주식 영업 일자

        @JsonProperty("stck_clpr")
        private String closePrice; // 종가

        @JsonProperty("stck_oprc")
        private String openPrice; // 시가

        @JsonProperty("stck_hgpr")
        private String highPrice; // 고가

        @JsonProperty("stck_lwpr")
        private String lowPrice; // 저가

        @JsonProperty("acml_vol")
        private String volume; // 누적 거래량
    }
}