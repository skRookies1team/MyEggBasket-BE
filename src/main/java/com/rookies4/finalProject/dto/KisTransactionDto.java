package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisTransactionDto {

    @JsonProperty("output1")
    private List<KisOrderDetail> output1;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KisOrderDetail {
        @JsonProperty("odno")
        private String orderNo; // 주문 번호

        @JsonProperty("pdno")
        private String stockCode; // 종목코드

        @JsonProperty("prdt_name")
        private String stockName; // 종목명

        @JsonProperty("sll_buy_dvsn_cd")
        private String buySellCode; // 01: 매도, 02: 매수

        @JsonProperty("ord_qty")
        private String orderQty; // 주문 수량

        @JsonProperty("tot_ccld_qty")
        private String filledQty; // 총 체결 수량

        @JsonProperty("cncl_cfrm_qty")
        private String cancelQty; // 취소 확인 수량

        @JsonProperty("avg_prvs")
        private String avgPrice; // 체결 평균가

        @JsonProperty("ord_dt")
        private String orderDate; // 주문일자

        @JsonProperty("ord_tmd")
        private String orderTime; // 주문시각
    }
}
