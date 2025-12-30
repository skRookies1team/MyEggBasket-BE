package com.rookies4.finalProject.service;

import com.rookies4.finalProject.component.KisApiClient;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.domain.enums.TransactionType;
import com.rookies4.finalProject.dto.KisApiRequest;
import com.rookies4.finalProject.dto.KisStockOrderDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisStockOrderService {

    private final KisApiClient kisApiClient;

    /**
     * KIS 주문 (매수 / 매도)
     */
    public KisStockOrderDTO.OrderResponse orderStock(
            boolean useVirtualServer,
            User user,
            KisStockOrderDTO.KisStockOrderRequest request
    ) {
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자 정보가 없습니다.");
        }

        // 요청값 검증
        Integer qty = request.getQuantity();
        Integer price = request.getPrice();

        if (qty == null || qty <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "주문 수량이 올바르지 않습니다.");
        }
        if (price == null || price <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "주문 가격이 올바르지 않습니다.");
        }

        // trade_id 결정
        String tradeId = resolveTradeId(useVirtualServer, request.getOrderType());

        // Body 구성
        
        Map<String, String> body = new HashMap<>();
        body.put("CANO", user.getAccount());
        body.put("ACNT_PRDT_CD", "01");
        body.put("PDNO", request.getStockCode());
        body.put("ORD_DVSN", "03");
        body.put("ORD_QTY", String.valueOf(qty));
        body.put("ORD_UNPR", "0");

        KisApiRequest apiRequest = KisApiRequest.builder()
                .path("/uapi/domestic-stock/v1/trading/order-cash")
                .trId(tradeId)
                .body(body)
                .useVirtualServer(useVirtualServer)
                .build();

        log.info("### KIS 주문 요청 ({}) ###", useVirtualServer ? "모의" : "실전");
        log.info("tr_id: {}", tradeId);

        return kisApiClient.post(user.getId(), apiRequest, KisStockOrderDTO.OrderResponse.class);
    }

    /**
     * KIS 지정가 주문 (매수 / 매도)
     */
    public KisStockOrderDTO.OrderResponse orderStockWithLimitPrice(
            boolean useVirtualServer,
            User user,
            KisStockOrderDTO.KisStockLimitPriceOrderRequest request
    ) {
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자 정보가 없습니다.");
        }

        // 요청값 검증
        Integer qty = request.getQuantity();
        Integer limitPrice = request.getLimitPrice();

        if (qty == null || qty <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "주문 수량이 올바르지 않습니다.");
        }
        if (limitPrice == null || limitPrice <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지정가가 올바르지 않습니다.");
        }

        // trade_id 결정
        String tradeId = resolveTradeId(useVirtualServer, request.getOrderType());

        // Body 구성
        Map<String, String> body = new HashMap<>();
        body.put("CANO", user.getAccount());
        body.put("ACNT_PRDT_CD", "01");
        body.put("PDNO", request.getStockCode());
        body.put("ORD_DVSN", "00");  // 지정가: 00
        body.put("ORD_QTY", String.valueOf(qty));
        body.put("ORD_UNPR", String.valueOf(limitPrice));

        KisApiRequest apiRequest = KisApiRequest.builder()
                .path("/uapi/domestic-stock/v1/trading/order-cash")
                .trId(tradeId)
                .body(body)
                .useVirtualServer(useVirtualServer)
                .build();

        log.info("### KIS 지정가 주문 요청 ({}) ###", useVirtualServer ? "모의" : "실전");
        log.info("tr_id: {}", tradeId);

        return kisApiClient.post(user.getId(), apiRequest, KisStockOrderDTO.OrderResponse.class);
    }

    /**
     * 매수 / 매도에 따른 trade_id 선택
     */
    private String resolveTradeId(boolean virtual, TransactionType type) {
        if (virtual) {
            return type == TransactionType.BUY ? "VTTC0802U" : "VTTC0801U";
        }
        return type == TransactionType.BUY ? "TTTC0802U" : "TTTC0801U";
    }
}
