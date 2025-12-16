package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rookies4.finalProject.domain.enums.TransactionType;
import com.rookies4.finalProject.domain.enums.TriggerSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class KisStockOrderDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KisStockOrderResponse {
        // API 응답의 "rt_cd" (0: 성공, 그 외: 실패)
        @JsonProperty("rt_cd")
        private String rtCd;

        // API 응답의 "msg_cd" (응답 코드)
        @JsonProperty("msg_cd")
        private String msgCd;

        // API 응답의 "msg1" (응답 메시지)
        @JsonProperty("msg1")
        private String msg1;

        // API 응답의 "output" (주문 상세 정보는 output 객체 안에 담겨옵니다)
        @JsonProperty("output")
        private KisStockOrderOutput output;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KisStockOrderOutput {
        // 주문번호 (Order Number)
        @JsonProperty("ODNO")
        private String orderNo;

        // 주문시각 (Order Time)
        @JsonProperty("ORD_TMD")
        private String orderTime;

        // 한국거래소 전송 주문 조직 번호
        @JsonProperty("KRX_FWDG_ORD_ORG_NO")
        private String krxFwdgOrdOrgNo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KisStockOrderRequest {
        private String stockCode;              // 종목코드
        private TransactionType orderType;     // BUY / SELL
        private Integer quantity;              // 주문수량
        private Integer price;                 // 주문단가
        private TriggerSource triggerSource;   // MANUAL / AI
    }
}