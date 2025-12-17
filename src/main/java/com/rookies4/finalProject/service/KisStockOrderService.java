package com.rookies4.finalProject.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.component.SecureLogger;
import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.KisAuthToken;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.domain.enums.TransactionType;
import com.rookies4.finalProject.dto.KisStockOrderDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.KisAuthRepository;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisStockOrderService {

    private final RestTemplate restTemplate;
    private final KisAuthRepository kisAuthRepository;
    private final ObjectMapper objectMapper;
    private final SecureLogger secureLogger;
    private final EncryptionUtil encryptionUtil;

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

        // ===== 1. 요청값 검증 =====
        Integer qty = request.getQuantity();
        Integer price = request.getPrice();

        if (qty == null || qty <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "주문 수량이 올바르지 않습니다.");
        }
        if (price == null || price <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "주문 가격이 올바르지 않습니다.");
        }

        // ===== 2. trade_id 결정 =====
        String tradeId = resolveTradeId(useVirtualServer, request.getOrderType());

        // ===== 3. KIS URL =====
        URI uri = KisApiConfig.uri(useVirtualServer,
                "/uapi/domestic-stock/v1/trading/order-cash");

        // ===== 4. 인증 토큰 =====
        KisAuthToken token = kisAuthRepository.findByUser(user)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "KIS 인증 토큰이 없습니다.")
                );

        // ===== 5. Headers =====
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", token.getTokenType() + " " + token.getAccessToken());
        headers.set("appkey", encryptionUtil.decrypt(user.getAppkey()));
        headers.set("appsecret", encryptionUtil.decrypt(user.getAppsecret()));
        headers.set("tr_id", tradeId);
        headers.set("custtype", "P");

        // ===== 6. Body =====
        Map<String, String> body = new HashMap<>();
        body.put("CANO", user.getAccount());
        body.put("ACNT_PRDT_CD", "01");
        body.put("PDNO", request.getStockCode());
        body.put("ORD_DVSN", "03");
        body.put("ORD_QTY", String.valueOf(qty));
        body.put("ORD_UNPR", "0");

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "주문 JSON 생성 실패");
        }

        HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);

        // ===== 7. 요청 로그 =====
        log.info("### KIS 주문 요청 ({} ) ###", useVirtualServer ? "모의" : "실전");
        log.info("URL: {}", uri);
        log.info("tr_id: {}", tradeId);
        log.info("Request Body: {}", secureLogger.maskSensitive(bodyJson));

        // ===== 8. 호출 =====
        try {
            ResponseEntity<KisStockOrderDTO.OrderResponse> response =
                    restTemplate.exchange(
                            uri,
                            HttpMethod.POST,
                            entity,
                            KisStockOrderDTO.OrderResponse.class
                    );

            try {
                log.info("KIS 주문 응답: {}", secureLogger.maskSensitiveJson(response.getBody()));
            } catch (JsonProcessingException e) {
                log.error("응답 로깅 중 오류 발생", e);
            }
            return response.getBody();

        } catch (RestClientResponseException e) {
            log.error("KIS 주문 실패 [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(
                    ErrorCode.KIS_API_ERROR,
                    "KIS 주문 실패: " + e.getResponseBodyAsString()
            );
        } catch (RestClientException e) {
            log.error("KIS API 통신 오류", e);
            throw new BusinessException(
                    ErrorCode.KIS_API_ERROR,
                    "KIS API 통신 중 오류가 발생했습니다."
            );
        }
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
