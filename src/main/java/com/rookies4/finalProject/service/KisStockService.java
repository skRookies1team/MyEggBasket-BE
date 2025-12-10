package com.rookies4.finalProject.service;

import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.dto.CurrentPriceDTO;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class KisStockService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final KisAuthService kisAuthService;

    public CurrentPriceDTO getCurrentPrice(String stockCode, boolean useVirtualServer, Long userId) {

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 토큰 발급
        KisAuthTokenDTO.KisTokenResponse tokenResponse =
                kisAuthService.issueToken(useVirtualServer, user);
        String accessToken = tokenResponse.getAccessToken();

        // 3. KIS API 호출
        String path = "/uapi/domestic-stock/v1/quotations/inquire-price";
        URI uri = KisApiConfig.uri(useVirtualServer, path);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri)
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", stockCode);

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", decodeBase64(user.getAppkey()));
        headers.set("appsecret", decodeBase64(user.getAppsecret()));
        headers.set("tr_id", "FHKST01010100");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    requestEntity,
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            if (!"0".equals(body.get("rt_cd"))) {
                throw new BusinessException(ErrorCode.KIS_API_ERROR,
                        "현재가 조회 실패: " + body.get("msg1"));
            }

            Map<String, Object> output = (Map<String, Object>) body.get("output");

            return CurrentPriceDTO.builder()
                    .stockCode(stockCode)
                    .currentPrice(parseNumber(output.get("stck_prpr")))
                    .changeAmount(parseNumber(output.get("prdy_vrss")))
                    .changeRate(parseNumber(output.get("prdy_ctrt")))
                    .volume(parseNumber(output.get("acml_vol")))
                    .tradingValue(parseNumber(output.get("acml_tr_pbmn")))
                    .openPrice(parseNumber(output.get("stck_oprc")))
                    .highPrice(parseNumber(output.get("stck_hgpr")))
                    .lowPrice(parseNumber(output.get("stck_lwpr")))
                    .build();

        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR,
                    "KIS API 호출 실패: " + e.getMessage());
        }
    }

    private Double parseNumber(Object value) {
        if (value == null) return 0.0;
        return Double.parseDouble(String.valueOf(value).replace(",", ""));
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isEmpty()) return encoded;
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encoded;
        }
    }
}