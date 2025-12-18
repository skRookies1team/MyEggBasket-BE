package com.rookies4.finalProject.component;

import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisApiRequest;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.service.KisAuthService;
import com.rookies4.finalProject.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * KIS API 통합 클라이언트
 * - 중복 코드 제거
 * - 에러 처리 통합
 * - Retry 로직 적용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisApiClient {

    private final RestTemplate restTemplate;
    private final KisAuthService kisAuthService;
    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;
    private final SecureLogger secureLogger;

    /**
     * KIS API GET 요청
     */
    public <T> T get(Long userId, KisApiRequest request, Class<T> responseType) {
        User user = getUser(userId);
        String accessToken = getAccessToken(user, request.isUseVirtualServer());
        HttpHeaders headers = buildHeaders(user, accessToken, request.getTrId());
        URI uri = buildUri(request.getPath(), request.getQueryParams(), request.isUseVirtualServer());

        return executeWithRetry(uri, HttpMethod.GET, headers, null, responseType);
    }

    /**
     * KIS API POST 요청
     */
    public <T> T post(Long userId, KisApiRequest request, Class<T> responseType) {
        User user = getUser(userId);
        String accessToken = getAccessToken(user, request.isUseVirtualServer());
        HttpHeaders headers = buildHeaders(user, accessToken, request.getTrId());
        URI uri = buildUri(request.getPath(), request.getQueryParams(), request.isUseVirtualServer());

        return executeWithRetry(uri, HttpMethod.POST, headers, request.getBody(), responseType);
    }

    /**
     * Retry 로직이 적용된 실행 메서드
     */
    @Retryable(
            retryFor = {RestClientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private <T> T executeWithRetry(URI uri, HttpMethod method, HttpHeaders headers,
                                   Object body, Class<T> responseType) {
        try {
            HttpEntity<?> entity = new HttpEntity<>(body, headers);

            log.debug("KIS API 호출: {} {}", method, uri);
            if (body != null) {
                try {
                    log.debug("Request Body: {}", secureLogger.maskSensitiveJson(body));
                } catch (Exception e) {
                    log.debug("Request Body masking failed: {}", e.getMessage());
                }
            }

            ResponseEntity<T> response = restTemplate.exchange(
                    uri.toString(),
                    method,
                    entity,
                    responseType
            );

            if (response.getBody() == null) {
                throw new BusinessException(
                        ErrorCode.KIS_API_ERROR,
                        "KIS API 응답이 없습니다"
                );
            }

            return response.getBody();

        } catch (RestClientResponseException e) {
            String errorBody = secureLogger.maskSensitive(e.getResponseBodyAsString());
            log.error("KIS API 실패 [{}]: {}", e.getStatusCode(), errorBody);

            throw new BusinessException(
                    ErrorCode.KIS_API_ERROR,
                    parseKisErrorMessage(e)
            );
        } catch (RestClientException e) {
            log.error("KIS API 호출 중 네트워크 오류: {}", e.getMessage(), e);
            throw new BusinessException(
                    ErrorCode.KIS_API_ERROR,
                    "KIS API 호출 실패: " + e.getMessage()
            );
        }
    }

    /**
     * 사용자 조회
     */
    private User getUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "사용자 ID가 null입니다.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다."
                ));
    }

    /**
     * Access Token 조회
     */
    private String getAccessToken(User user, boolean useVirtualServer) {
        KisAuthTokenDTO.KisTokenResponse tokenResponse =
                kisAuthService.issueToken(useVirtualServer, user);
        return tokenResponse.getAccessToken();
    }

    /**
     * HTTP 헤더 구성
     */
    private HttpHeaders buildHeaders(User user, String accessToken, String trId) {
        // 복호화 및 검증
        String decryptedAppkey = encryptionUtil.decrypt(user.getAppkey());
        String decryptedAppsecret = encryptionUtil.decrypt(user.getAppsecret());

        // 트림 처리
        String appkey = decryptedAppkey == null ? null : decryptedAppkey.trim();
        String appsecret = decryptedAppsecret == null ? null : decryptedAppsecret.trim();

        if (appkey == null || appkey.isEmpty()) {
            log.error("복호화된 appkey가 유효하지 않습니다. userId={}", user.getId());
            throw new BusinessException(ErrorCode.KIS_API_KEY_NOT_FOUND, "KIS API 키가 유효하지 않습니다.");
        }
        if (appsecret == null || appsecret.isEmpty()) {
            log.error("복호화된 appsecret이 유효하지 않습니다. userId={}", user.getId());
            throw new BusinessException(ErrorCode.KIS_API_SECRET_NOT_FOUND, "KIS API Secret이 유효하지 않습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appkey);
        headers.set("appsecret", appsecret);
        headers.set("tr_id", trId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return headers;
    }

    /**
     * URI 구성
     */
    private URI buildUri(String path, Map<String, String> queryParams, boolean useVirtualServer) {
        URI baseUri = KisApiConfig.uri(useVirtualServer, path);
        if (baseUri == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "KIS API URI 생성 실패");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(baseUri);

        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(builder::queryParam);
        }

        return builder.build().toUri();
    }

    /**
     * KIS 에러 메시지 파싱
     */
    private String parseKisErrorMessage(RestClientResponseException e) {
        try {
            String body = e.getResponseBodyAsString();
            // 간단한 JSON 파싱 (실제로는 더 정교하게 처리 가능)
            if (body.contains("msg1")) {
                int start = body.indexOf("msg1") + 7;
                int end = body.indexOf("\"", start);
                if (end > start) {
                    return body.substring(start, end);
                }
            }
            return "KIS API 오류: " + e.getStatusCode();
        } catch (Exception ex) {
            return "KIS API 오류: " + e.getStatusCode();
        }
    }
}
