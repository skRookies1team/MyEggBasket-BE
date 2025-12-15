package com.rookies4.finalProject.service;

import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.dto.KisStockOrderDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.util.Base64Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisStockOrderService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final KisAuthService kisAuthService;

    @Transactional
    public KisStockOrderDTO.OrderResponse orderStock(boolean useVirtualServer, User user, KisStockOrderDTO.KisStockOrderRequest orderRequest) {
        
        KisAuthTokenDTO.KisTokenResponse tokenResponse = kisAuthService.issueToken(useVirtualServer, user);
        String accessToken = tokenResponse.getAccessToken();

        String path = useVirtualServer ? "/uapi/virtual-stock/v1/trading/order-cash" : "/uapi/real-stock/v1/trading/order-cash"; // 실전/모의 경로 분기
        URI uri = KisApiConfig.uri(useVirtualServer, path);

        // orderRequest에서 필요한 값 추출
        String stockCode = orderRequest.getStockCode();
        String orderType = orderRequest.getOrderId(); // "매수", "매도"
        int quantity = Integer.parseInt(orderRequest.getOrderQuantity());
        int price = 0; // 시장가 주문을 위해 0으로 설정 (필요시 orderRequest에서 받아오도록 수정)

        // 주문 요청 바디 생성
        Map<String, String> requestBody = Map.of(
                "CANO", user.getAccount(),
                "ACNT_PRDT_CD", "01",
                "PDNO", stockCode,
                "ORD_DVSN", getOrderDivision(orderType, price),
                "ORD_QTY", String.valueOf(quantity),
                "ORD_UNPR", String.valueOf(price)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", Base64Util.decode(user.getAppkey()));
        headers.set("appsecret", Base64Util.decode(user.getAppsecret()));
        headers.set("tr_id", useVirtualServer ? "VTTC0802U" : "TTTC0802U"); // 모의투자/실전투자 TR_ID 분기

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            KisStockOrderDTO.OrderResponse response = restTemplate.postForObject(
                    uri,
                    requestEntity,
                    KisStockOrderDTO.OrderResponse.class
            );

            if (response == null || !"0".equals(response.getRtCd())) {
                String msg = response != null ? response.getMsg1() : "응답이 없습니다.";
                throw new BusinessException(ErrorCode.KIS_API_ERROR, "주문 실패: " + msg);
            }

            return response;

        } catch (RestClientException e) {
            log.error("KIS 주문 API 호출 실패", e);
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "주문 API 호출에 실패했습니다.");
        }
    }

    private String getOrderDivision(String orderType, int price) {
        // "매수", "매도" 등의 orderType에 따라 KIS API가 요구하는 코드로 변환하는 로직 추가 필요
        // 현재는 가격에 따라서만 지정가/시장가 구분
        if (price == 0) {
            return "01"; // 시장가
        }
        return "00"; // 지정가
    }
}