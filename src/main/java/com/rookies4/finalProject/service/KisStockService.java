package com.rookies4.finalProject.service;

import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.CurrentPriceDTO;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KisStockService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final StockRepository stockRepository; // StockRepository 주입
    private final KisAuthService kisAuthService;

    public CurrentPriceDTO getCurrentPrice(String stockCode, boolean useVirtualServer, Long userId) {

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 종목코드 기본 형식 검증
        validateStockCodeFormat(stockCode);

        // 3. 토큰 발급
        KisAuthTokenDTO.KisTokenResponse tokenResponse =
                kisAuthService.issueToken(useVirtualServer, user);
        String accessToken = tokenResponse.getAccessToken();

        // 4. KIS API 호출
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
            if (body == null || !"0".equals(body.get("rt_cd"))) {
                String msg = body != null ? (String) body.get("msg1") : "응답이 없습니다.";
                throw new BusinessException(ErrorCode.KIS_API_ERROR, "현재가 조회 실패: " + msg);
            }

            Map<String, Object> output = (Map<String, Object>) body.get("output");
            if (output == null) {
                throw new BusinessException(ErrorCode.KIS_API_ERROR, "현재가 조회 결과(output)가 없습니다.");
            }

            // 5. DB에서 종목명 조회
            String stockName = stockRepository.findById(stockCode)
                    .map(Stock::getName)
                    .orElse(null); // DB에 없으면 null

            // 현재가 파싱
            BigDecimal currentPrice = parseBigDecimal(output.get("stck_prpr"));

            return CurrentPriceDTO.builder()
                    .stockCode(stockCode)
                    .stockName(stockName) // DB에서 조회한 종목명 설정
                    .currentPrice(currentPrice)
                    .changeAmount(parseDouble(output.get("prdy_vrss")))
                    .changeRate(parseDouble(output.get("prdy_ctrt")))
                    .volume(parseLong(output.get("acml_vol")))
                    .tradingValue(parseDouble(output.get("acml_tr_pbmn")))
                    .openPrice(parseDouble(output.get("stck_oprc")))
                    .highPrice(parseDouble(output.get("stck_hgpr")))
                    .lowPrice(parseDouble(output.get("stck_lwpr")))
                    .closePrice(currentPrice.doubleValue())
                    .updatedAt(LocalDateTime.now())
                    .build();

        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR,
                    "KIS API 호출 실패: " + e.getMessage());
        }
    }

    private void validateStockCodeFormat(String stockCode) {
        if (stockCode == null || stockCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "종목코드를 입력해주세요.");
        }
        if (!stockCode.matches("^\\d{6}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "올바른 종목코드 형식이 아닙니다. (6자리 숫자 필요)");
        }
    }

    private Double parseDouble(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return 0.0;
        return Double.parseDouble(String.valueOf(value).replace(",", ""));
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(value).replace(",", ""));
    }

    private Long parseLong(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return 0L;
        return Long.parseLong(String.valueOf(value).replace(",", ""));
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Base64 디코딩 실패: {}", encoded, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "잘못된 형식의 인증 정보입니다.");
        }
    }
}