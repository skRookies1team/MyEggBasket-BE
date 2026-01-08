package com.rookies4.finalProject.dto.kafka;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookDTO {

    private String type; // 메시지 타입 ("ORDER_BOOK")

    private String stockCode; // 종목 코드 (MKSC_SHRN_ISCD)

    private List<OrderItem> asks; // 매도 호가 리스트 (가격 & 잔량)
    private List<OrderItem> bids; // 매수 호가 리스트 (가격 & 잔량)

    private Long totalAskQty; // 총 매도호가 잔량 (TOTAL_ASKP_RSQN)
    private Long totalBidQty; // 총 매수호가 잔량 (TOTAL_BIDP_RSQN)

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {

        /**
         * 호가 가격
         * - 매도: ASKP1~ASKP10
         * - 매수: BIDP1~BIDP10
         */
        private BigDecimal price;

        /**
         * 호가 잔량
         * - 매도: ASKP_RSQN1~ASKP_RSQN10
         * - 매수: BIDP_RSQN1~BIDP_RSQN1
         */
        private Long qty;
    }
}