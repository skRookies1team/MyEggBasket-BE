package com.rookies4.finalProject.component;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * - Retry 로직 (수동 구현) 적용
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
    private final ObjectMapper objectMapper;

    /**
     * KIS API GET 요청
     */
    public <T> T get(Long userId, KisApiRequest request, Class<T> responseType) {
        User user = getUser(userId);
        try {
            return executeInternal(user, request, responseType, HttpMethod.GET);
        } catch (BusinessException e) {
            if (isTokenExpiredError(e)) {
                log.warn("KIS 토큰 만료 감지 (GET). 토큰 재발급 후 재시도합니다. userId={}", userId);
                kisAuthService.expireToken(user);
                return executeInternal(user, request, responseType, HttpMethod.GET);
            }
            throw e;
        }
    }

    /**
     * KIS API POST 요청
     */
    public <T> T post(Long userId, KisApiRequest request, Class<T> responseType) {
        User user = getUser(userId);
        try {
            return executeInternal(user, request, responseType, HttpMethod.POST);
        } catch (BusinessException e) {
            if (isTokenExpiredError(e)) {
                log.warn("KIS 토큰 만료 감지 (POST). 토큰 재발급 후 재시도합니다. userId={}", userId);
                kisAuthService.expireToken(user);
                return executeInternal(user, request, responseType, HttpMethod.POST);
            }
            throw e;
        }
    }

    private <T> T executeInternal(User user, KisApiRequest request, Class<T> responseType, HttpMethod method) {
        String accessToken = getAccessToken(user, request.isUseVirtualServer());

        String jsonBody = null;
        if (request.getBody() != null) {
            try {
                if (request.getBody() instanceof String) {
                    jsonBody = (String) request.getBody();
                } else {
                    jsonBody = objectMapper.writeValueAsString(request.getBody());
                }
            } catch (Exception e) {
                log.error("JSON Body serialization failed", e);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "요청 데이터 변환 실패");
            }
        }

        HttpHeaders headers = buildHeaders(user, accessToken, request.getTrId(), jsonBody);
        URI uri = buildUri(request.getPath(), request.getQueryParams(), request.isUseVirtualServer());

        return executeWithRetry(uri, method, headers, jsonBody, responseType);
    }

    private boolean isTokenExpiredError(BusinessException e) {
        String msg = e.getMessage();
        String detail = e.getDetail();
        boolean inMsg = msg != null && (msg.contains("EGW00123") || msg.contains("만료된 token") || msg.contains("expired"));
        boolean inDetail = detail != null && (detail.contains("EGW00123") || detail.contains("만료된 token") || detail.contains("expired"));
        return inMsg || inDetail;
    }

    /**
     * [핵심 변경] 수동 Retry 로직 구현
     * - @Retryable 제거 (BusinessException 변환 문제 해결)
     * - while 루프를 사용하여 에러 발생 시 재시도
     */
    private <T> T executeWithRetry(URI uri, HttpMethod method, HttpHeaders headers,
                                   Object body, Class<T> responseType) {
        int maxAttempts = 3; // 최대 3회 시도
        int attempt = 0;

        // 바디 직렬화
        String jsonBody = null;
        if (body != null) {
            try {
                if (body instanceof String) jsonBody = (String) body;
                else jsonBody = objectMapper.writeValueAsString(body);
                log.debug("Request Body: {}", secureLogger.maskSensitive(jsonBody));
            } catch (Exception e) {
                log.warn("Request Body parsing failed: {}", e.getMessage());
                jsonBody = body.toString();
            }
        }

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        // 재시도 루프 시작
        while (attempt < maxAttempts) {
            attempt++;
            try {
                // 1. 기본 속도 제한 (0.1초 대기) - 이전 요청과의 간격 확보
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }

                log.debug("KIS API 호출 (시도 {}/{}): {} {}", attempt, maxAttempts, method, uri);

                ResponseEntity<T> response = restTemplate.exchange(
                        uri.toString(),
                        method,
                        entity,
                        responseType
                );

                if (response.getBody() == null) {
                    throw new BusinessException(ErrorCode.KIS_API_ERROR, "KIS API 응답이 없습니다");
                }

                return response.getBody();

            } catch (RestClientResponseException e) {
                String responseBody = e.getResponseBodyAsString();
                String errorMsg = secureLogger.maskSensitive(responseBody);

                // [체크] "초당 거래건수 초과" 또는 5xx 서버 에러인지 확인
                boolean isRateLimit = responseBody.contains("초당 거래건수") || responseBody.contains("EGW00201");
                boolean isServerError = e.getStatusCode().is5xxServerError();

                if (isRateLimit || isServerError) {
                    // 최대 횟수에 도달했으면 에러 던짐
                    if (attempt >= maxAttempts) {
                        log.error("KIS API 최종 실패 [{}]: {}", e.getStatusCode(), errorMsg);
                        throw new BusinessException(ErrorCode.KIS_API_ERROR, parseKisErrorMessage(e));
                    }

                    // 재시도 가능한 에러 -> 대기 후 재시도 (Backoff)
                    log.warn("KIS API 일시적 오류 발생 (Rate Limit/Server Error). 1초 후 재시도합니다. (시도 {}/{}) - Msg: {}", attempt, maxAttempts, parseKisErrorMessage(e));
                    try {
                        Thread.sleep(1000); // 1초 대기
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue; // while 루프의 처음으로 돌아가 재시도
                }

                // 재시도 불가능한 에러 (4xx 등) -> 즉시 실패
                log.error("KIS API 실패 [{}]: {}", e.getStatusCode(), errorMsg);
                throw new BusinessException(ErrorCode.KIS_API_ERROR, parseKisErrorMessage(e));

            } catch (RestClientException e) {
                // 네트워크 오류 등 -> 재시도
                if (attempt >= maxAttempts) {
                    log.error("KIS API 호출 중 네트워크 오류 (최종): {}", e.getMessage());
                    throw new BusinessException(ErrorCode.KIS_API_ERROR, "KIS API 호출 실패: " + e.getMessage());
                }

                log.warn("KIS API 네트워크 오류. 1초 후 재시도합니다. (시도 {}/{}) - Error: {}", attempt, maxAttempts, e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                // continue
            }
        }

        throw new BusinessException(ErrorCode.KIS_API_ERROR, "KIS API 호출 횟수 초과");
    }

    private User getUser(Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "사용자 ID가 null입니다.");
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private String getAccessToken(User user, boolean useVirtualServer) {
        KisAuthTokenDTO.KisTokenResponse tokenResponse = kisAuthService.issueToken(useVirtualServer, user);
        return tokenResponse.getAccessToken();
    }

    private HttpHeaders buildHeaders(User user, String accessToken, String trId, String jsonBody) {
        String decryptedAppkey = encryptionUtil.decrypt(user.getAppkey());
        String decryptedAppsecret = encryptionUtil.decrypt(user.getAppsecret());
        String appkey = decryptedAppkey == null ? null : decryptedAppkey.trim();
        String appsecret = decryptedAppsecret == null ? null : decryptedAppsecret.trim();

        if (appkey == null || appkey.isEmpty()) throw new BusinessException(ErrorCode.KIS_API_KEY_NOT_FOUND, "KIS API 키가 유효하지 않습니다.");
        if (appsecret == null || appsecret.isEmpty()) throw new BusinessException(ErrorCode.KIS_API_SECRET_NOT_FOUND, "KIS API Secret이 유효하지 않습니다.");

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appkey);
        headers.set("appsecret", appsecret);
        headers.set("tr_id", trId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (jsonBody != null && !jsonBody.isEmpty()) {
            try {
                String hashkey = kisAuthService.getHashKey(user, jsonBody);
                headers.set("hashkey", hashkey);
            } catch (Exception e) {
                log.error("HashKey 생성 실패", e);
            }
        }
        return headers;
    }

    private URI buildUri(String path, Map<String, String> queryParams, boolean useVirtualServer) {
        URI baseUri = KisApiConfig.uri(useVirtualServer, path);
        if (baseUri == null) throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "KIS API URI 생성 실패");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(baseUri);
        if (queryParams != null && !queryParams.isEmpty()) queryParams.forEach(builder::queryParam);
        return builder.build().toUri();
    }

    private String parseKisErrorMessage(RestClientResponseException e) {
        try {
            String body = e.getResponseBodyAsString();
            if (body.contains("msg1")) {
                int start = body.indexOf("msg1") + 7;
                int end = body.indexOf("\"", start);
                String msg1 = (end > start) ? body.substring(start, end) : "";
                if(body.contains("EGW00123")) return msg1 + " (EGW00123)";
                return msg1;
            }
            return "KIS API 오류: " + e.getStatusCode();
        } catch (Exception ex) {
            return "KIS API 오류: " + e.getStatusCode();
        }
    }
}