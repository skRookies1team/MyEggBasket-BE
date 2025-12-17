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
import com.rookies4.finalProject.util.EncryptionUtil; // EncryptionUtil 임포트
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
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KisStockService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final KisAuthService kisAuthService;
    private final EncryptionUtil encryptionUtil;

    public CurrentPriceDTO getCurrentPrice(String stockCode, boolean useVirtualServer, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateStockCodeFormat(stockCode);
        KisAuthTokenDTO.KisTokenResponse tokenResponse =
                kisAuthService.issueToken(useVirtualServer, user);
        String accessToken = tokenResponse.getAccessToken();

        String path = "/uapi/domestic-stock/v1/quotations/inquire-price";
        URI uri = KisApiConfig.uri(useVirtualServer, path);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri)
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", stockCode);

        try {
            // 복호화 및 검증
            String decryptedAppkey = encryptionUtil.decrypt(user.getAppkey());
            String decryptedAppsecret = encryptionUtil.decrypt(user.getAppsecret());

            // 복호화된 값을 트림하여 사용
            String appkey = decryptedAppkey == null ? null : decryptedAppkey.trim();
            String appsecret = decryptedAppsecret == null ? null : decryptedAppsecret.trim();

            if (appkey == null || appkey.isEmpty()) {
                log.error("복호화된 appkey가 유효하지 않습니다. userId={}", userId);
                throw new BusinessException(ErrorCode.KIS_API_KEY_NOT_FOUND, "KIS API 키가 유효하지 않습니다.");
            }
            if (appsecret == null || appsecret.isEmpty()) {
                log.error("복호화된 appsecret이 유효하지 않습니다. userId={}", userId);
                throw new BusinessException(ErrorCode.KIS_API_SECRET_NOT_FOUND, "KIS API Secret이 유효하지 않습니다.");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("authorization", "Bearer " + accessToken);
            headers.set("appkey", appkey);
            headers.set("appsecret", appsecret);
            headers.set("tr_id", "FHKST01010100");
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
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

            String stockName = stockRepository.findById(stockCode)
                    .map(Stock::getName)
                    .orElse((String) output.get("hts_kor_isnm"));

            BigDecimal currentPrice = parseBigDecimal(output.get("stck_prpr"));

            return CurrentPriceDTO.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .currentPrice(currentPrice)
                    .changeAmount(parseDouble(output.get("prdy_vrss")))
                    .changeRate(parseDouble(output.get("prdy_ctrt")))
                    .volume(parseLong(output.get("acml_vol")))
                    .tradingValue(parseDouble(output.get("acml_tr_pbmn")))
                    .openPrice(parseDouble(output.get("stck_oprc")))
                    .highPrice(parseDouble(output.get("stck_hgpr")))
                    .lowPrice(parseDouble(output.get("stck_lwpr")))
                    .closePrice(currentPrice.doubleValue())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();

        } catch (BusinessException e) {
            // BusinessException은 이미 적절한 에러 코드와 메시지를 가지고 있으므로 재전파
            throw e;
        } catch (RestClientException e) {
            log.error("KIS API 호출 중 네트워크 오류 발생. stockCode={}, userId={}", stockCode, userId, e);
            throw new BusinessException(ErrorCode.KIS_API_ERROR,
                    "KIS API 호출 실패: " + e.getMessage());
        } catch (Exception e) {
            log.error("현재가 조회 중 예상치 못한 오류 발생. stockCode={}, userId={}", stockCode, userId, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "현재가 조회 중 오류가 발생했습니다.");
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
}