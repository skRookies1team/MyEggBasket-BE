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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class KisStockOrderService {

    private static final Logger log = LoggerFactory.getLogger(KisStockOrderService.class);

    private final RestTemplate restTemplate;
    private final KisAuthRepository kisAuthRepository;

    /**
     * tradeId를 선택합니다
     * @param useVirtualServer 모의투자인지, 실전투자인지 선택
     * @param orderId "매수","매도" 선택
     * @return 지정된 매수,매도 tradeId로 넘김
     * **/
    private String chooseTradeId(boolean useVirtualServer, String orderId){
        if(useVirtualServer){
            if("매수".equals(orderId)){
                return "VTTC0802U"; // 모의투자 매수 (API 문서 기준 확인 필요, 보통 VTTC0802U)
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
        headers.set("authorization", kisAuthToken.getTokenType() + kisAuthToken.getAccessToken());
        headers.set("appkey", decodedAppkey);
        headers.set("appsecret", decodedAppsecret);
        headers.set("tr_id", tradeId);
        headers.set("custtype", "P"); // 개인: P, 법인: B (일반적으로 P 사용)

        Map<String, String> body = new HashMap<>();
        body.put("CANO", account);             // 종합계좌번호 (8자리)
        body.put("ACNT_PRDT_CD", "01");        // 계좌상품코드 (보통 01)
        body.put("PDNO", orderRequest.getStockCode()); // 종목코드
        body.put("ORD_DVSN", "00");            // [추가] 주문구분 (00: 지정가, 01: 시장가 등)
        body.put("ORD_QTY", orderRequest.getOrderQuantity()); // 주문수량
        body.put("ORD_UNPR", orderRequest.getOrderPrice());   // 주문단가

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<KisStockOrderDTO.KisStockOrderResponse> response =
                    restTemplate.exchange(uri, HttpMethod.POST, request, KisStockOrderDTO.KisStockOrderResponse.class);

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