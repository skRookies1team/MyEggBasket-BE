package com.rookies4.finalProject.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.KisAuthToken;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisForeignIndexDTO;
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class KisForeignIndexService {

    private static final Logger log = LoggerFactory.getLogger(KisForeignIndexService.class);


    private final RestTemplate restTemplate;
    private final KisAuthRepository kisAuthRepository;
    private final ObjectMapper objectMapper;


    public KisForeignIndexDTO.KisForeignIndexResponse showForeignIndex(
            User user, KisForeignIndexDTO.KisForeignIndexRequest indexCode) {
        String path = "/uapi/overseas-price/v1/quotations/inquire-time-indexchartprice";

        URI uri = KisApiConfig.uri(false, path);

        // 인증 토큰 조회
        KisAuthToken kisAuthToken = kisAuthRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "token이 존재하지 않습니다."));

        String decodedAppkey = KisApiConfig.decodeBase64(user.getAppkey());
        String decodedAppsecret = KisApiConfig.decodeBase64(user.getAppsecret());
        String tradeId = "FHKST03030200";

        //RequestHeader
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("content-type", "application/json; charset=utf-8");
        headers.set("authorization", kisAuthToken.getTokenType() + " " + kisAuthToken.getAccessToken());
        headers.set("appkey", decodedAppkey);
        headers.set("appsecret", decodedAppsecret);
        headers.set("tr_id", tradeId);


        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("FID_COND_MRKT_DIV_CODE", "N");
        bodyMap.put("FID_INPUT_ISCD", indexCode.getIndexCode()); // DTO 필드명 수정 (indexCode -> fidInputIscd)
        bodyMap.put("FID_HOUR_CLS_CODE", "0");
        bodyMap.put("FID_PW_DATA_INCU_YN", "N");

        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(bodyMap);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "데이터 변환에 실패했습니다.");
        }

        HttpEntity<String> request = new HttpEntity<>(requestBodyJson, headers);

        try {
            ResponseEntity<KisForeignIndexDTO.KisForeignIndexResponse> response =
                    restTemplate.exchange(uri, HttpMethod.GET, request, KisForeignIndexDTO.KisForeignIndexResponse.class);

            log.info("KIS 성공 응답: {}", response.getBody());
            return response.getBody();


        } catch (RestClientResponseException e) {
            log.error("KIS API 호출 실패 (HTTP {}): 요청 URI: {}, 응답 Body: {}",
                    e.getStatusCode(), uri, e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.KIS_API_ERROR,
                    String.format("KIS API 호출 실패. [HTTP %s] %s",
                            e.getStatusCode(), e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            log.error("KIS API 호출 중 RestClient 오류 발생: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.KIS_API_ERROR,
                    "KIS API 호출 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}