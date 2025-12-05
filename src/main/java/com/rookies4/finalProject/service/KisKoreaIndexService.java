package com.rookies4.finalProject.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.KisAuthToken;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisForeignIndexDTO;
import com.rookies4.finalProject.dto.KisKoreaIndexDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.KisAuthRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KisKoreaIndexService {

    private static final Logger log = LoggerFactory.getLogger(KisKoreaIndexService.class);

    private final RestTemplate restTemplate;
    private final KisAuthRepository kisAuthRepository;
    private final ObjectMapper objectMapper;

    public KisKoreaIndexDTO.KisKoreaIndexResponse showKoreaIndex(
            User user, KisKoreaIndexDTO.KisKoreaIndexRequest indexCode){

        String path = "/uapi/domestic-stock/v1/quotations/inquire-index-tickprice";

        // 인증 토큰 조회
        KisAuthToken kisAuthToken = kisAuthRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "token이 존재하지 않습니다."));

        String decodedAppkey = KisApiConfig.decodeBase64(user.getAppkey());
        String decodedAppsecret = KisApiConfig.decodeBase64(user.getAppsecret());
        String tradeId = "FHPUP02110100";

        //RequestHeader
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("content-type", "application/json; charset=utf-8");
        headers.set("authorization", kisAuthToken.getTokenType() + " " + kisAuthToken.getAccessToken());
        headers.set("appkey", decodedAppkey);
        headers.set("appsecret", decodedAppsecret);
        headers.set("tr_id", tradeId);
        headers.set("custtype", "P");

        Map<String, String> params = new HashMap<>();
        params.put("FID_INPUT_ISCD", indexCode.getIndexCode()); // 코스피/코스닥 코드 (예: 1001)
        params.put("FID_COND_MRKT_DIV_CODE", "U");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        URI uriWithParams = KisApiConfig.uri(false, path, params);

        try {
            ResponseEntity<String> responseString =
                    restTemplate.exchange(uriWithParams, HttpMethod.GET, request, String.class);

            ResponseEntity<KisKoreaIndexDTO.KisKoreaIndexResponse> response =
                    restTemplate.exchange(uriWithParams, HttpMethod.GET, request, KisKoreaIndexDTO.KisKoreaIndexResponse.class);

            log.info("KIS 성공 응답: {}", response.getBody());
            log.info("KIS String 응답 : {}", responseString.getBody());
            return response.getBody();


        } catch (RestClientResponseException e) {
            // 5. HTTP 4xx/5xx 오류 상세 로깅
            log.error("KIS API 호출 실패 (HTTP {}): 요청 URI: {}, 응답 Body: {}",
                    e.getStatusCode(), uriWithParams, e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.KIS_API_ERROR,
                    String.format("KIS API 호출 실패. [HTTP %s] %s",
                            e.getStatusCode(), e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            // 6. RestTemplate 일반 오류 (네트워크, JSON 파싱 등) 상세 로깅
            log.error("KIS API 호출 중 RestClient 오류 발생: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.KIS_API_ERROR,
                    "KIS API 호출 중 오류가 발생했습니다: " + e.getMessage());
        }
    }



}
