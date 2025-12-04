package com.rookies4.finalProject.service;

import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.KisAuthToken;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisStockOrderDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.KisAuthRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper; // 💡 ObjectMapper import 추가

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors; // Collectors import 유지 (로깅용)

@Service
@Transactional
@RequiredArgsConstructor
public class KisStockOrderService {

    private static final Logger log = LoggerFactory.getLogger(KisStockOrderService.class);

    private final RestTemplate restTemplate;
    private final KisAuthRepository kisAuthRepository;
    private final ObjectMapper objectMapper;

    /**
     * tradeId를 선택합니다
     * @param useVirtualServer 모의투자인지, 실전투자인지 선택
     * @param orderId "매수","매도" 선택
     * @return 지정된 매수,매도 tradeId로 넘김
     * **/
    private String chooseTradeId(boolean useVirtualServer, String orderId){
        if(useVirtualServer){
            if("매수".equals(orderId)){
                return "VTTC0802U"; // 모의투자 매수
            } else {
                return "VTTC0801U"; // 모의투자 매도
            }
        } else {
            if("매수".equals(orderId)){
                return "TTTC0802U"; // 실전투자 매수
            } else {
                return "TTTC0801U"; // 실전투자 매도
            }
        }
    }

    public KisStockOrderDTO.KisStockOrderResponse orderStock(boolean useVirtualServer, User user, KisStockOrderDTO.KisStockOrderRequest orderRequest){
        String path = "/uapi/domestic-stock/v1/trading/order-cash";

        // 사용자 검증
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
        }

        URI uri = KisApiConfig.uri(useVirtualServer, path);

        // 인증 토큰 조회
        KisAuthToken kisAuthToken = kisAuthRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,"token이 존재하지 않습니다."));

        String decodedAppkey = KisApiConfig.decodeBase64(user.getAppkey());
        String decodedAppsecret = KisApiConfig.decodeBase64(user.getAppsecret());
        String tradeId = chooseTradeId(useVirtualServer, orderRequest.getOrderId());
        String account = user.getAccount();


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("content-type","application/json; charset=utf-8");
        // 토큰은 보안상 전체를 로깅하지 않습니다.
        headers.set("authorization", kisAuthToken.getTokenType() +" "+ kisAuthToken.getAccessToken());
        headers.set("appkey", decodedAppkey);
        // appsecret은 보안상 로깅하지 않습니다.
        headers.set("appsecret", decodedAppsecret);
        headers.set("tr_id", tradeId);
        headers.set("custtype", "P"); // 개인: P, 법인: B (일반적으로 P 사용)

        // ====================================================================
        // 💡 [수정] 요청 Body Map을 생성하고 JSON String으로 변환
        // ====================================================================
        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("CANO", account);
        bodyMap.put("ACNT_PRDT_CD", "01");
        bodyMap.put("PDNO", orderRequest.getStockCode());
        bodyMap.put("ORD_DVSN", "01");
        bodyMap.put("ORD_QTY", orderRequest.getOrderQuantity());
        bodyMap.put("ORD_UNPR", "0");

        String requestBodyJson;
        try {
            // Map을 명시적으로 JSON 문자열로 변환 (직렬화)
            requestBodyJson = objectMapper.writeValueAsString(bodyMap);
        } catch (JsonProcessingException e) {
            log.error("JSON 직렬화 오류 발생: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "주문 데이터 변환에 실패했습니다.");
        }

        // HttpEntity를 String Body와 Headers로 생성
        HttpEntity<String> request = new HttpEntity<>(requestBodyJson, headers);
        // ====================================================================


        // 💡 [수정] 로깅 부분: String 변수를 직접 로깅
        log.info("### KIS 주문 요청 전체 정보 로깅 시작 (서버: {}) ###", useVirtualServer ? "모의투자" : "실전투자");
        log.info("KIS URL: {}", uri);
        log.info("거래 구분 (tr_id): {}", tradeId);

        // 1. Headers 로깅 (보안상 마스킹 처리 유지)
        log.info("--- Headers ---");
        log.info("Authorization: {} ...", kisAuthToken.getTokenType());
        log.info("appkey: {}", decodedAppkey);
        log.info("appsecret: {} ...", decodedAppsecret.substring(0, Math.min(5, decodedAppsecret.length())));
        log.info("tr_id: {}", tradeId);
        log.info("Content-Type: {}", headers.getContentType());
        log.info("custtype: {}", headers.get("custtype"));

        // 2. Body 로깅
        log.info("--- Body ---");
        log.info("Request Body (JSON): {}", requestBodyJson); // 명시적으로 변환된 JSON String 로깅

        log.info("### KIS 주문 요청 전체 정보 로깅 종료 ###");
        // 💡 로깅 부분 끝

        try {
            // HttpEntity<String>으로 요청을 보내고, 응답은 KisStockOrderResponse 클래스로 받음
            ResponseEntity<KisStockOrderDTO.KisStockOrderResponse> response =
                    restTemplate.exchange(uri, HttpMethod.POST, request, KisStockOrderDTO.KisStockOrderResponse.class);

            // 성공 시 응답도 로깅
            log.info("KIS 주문 성공 응답: {}", response.getBody());
            return response.getBody();

        } catch (RestClientResponseException e) {
            log.error("KIS 주문 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.KIS_API_ERROR,
                    String.format("KIS 주문 API 호출 실패. [HTTP %s] %s",
                            e.getStatusCode(), e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            log.error("KIS API 호출 중 오류 발생: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.KIS_API_ERROR,
                    "KIS API 호출 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}