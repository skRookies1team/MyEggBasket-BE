package com.rookies4.finalProject.component;

import com.fasterxml.jackson.databind.ObjectMapper; // [추가됨] JSON 변환을 위한 ObjectMapper 임포트
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
    private final ObjectMapper objectMapper; // [추가됨] JSON 직렬화를 위해 주입

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
                kisAuthService.expireToken(user); // 토큰 만료 처리
                return executeInternal(user, request, responseType, HttpMethod.GET); // 재시도
            }
            throw e;
        }
    }

    /**
     * KIS API POST 요청 (재시도 로직 추가)
     */
    public <T> T post(Long userId, KisApiRequest request, Class<T> responseType) {
        User user = getUser(userId);
        try {
            return executeInternal(user, request, responseType, HttpMethod.POST);
        } catch (BusinessException e) {
            if (isTokenExpiredError(e)) {
                log.warn("KIS 토큰 만료 감지 (POST). 토큰 재발급 후 재시도합니다. userId={}", userId);
                kisAuthService.expireToken(user); // 토큰 만료 처리
                return executeInternal(user, request, responseType, HttpMethod.POST); // 재시도
            }
            throw e;
        }
    }

    // [추가] 실제 요청 실행 로직 분리
    private <T> T executeInternal(User user, KisApiRequest request, Class<T> responseType, HttpMethod method) {
        String accessToken = getAccessToken(user, request.isUseVirtualServer());
        HttpHeaders headers = buildHeaders(user, accessToken, request.getTrId());
        URI uri = buildUri(request.getPath(), request.getQueryParams(), request.isUseVirtualServer());

        return executeWithRetry(uri, method, headers, request.getBody(), responseType);
    }

    // [추가] 토큰 만료 에러인지 확인
    private boolean isTokenExpiredError(BusinessException e) {
        // 1. 기본 메시지 확인 (ErrorCode의 메시지)
        String msg = e.getMessage();

        // 2. 상세 메시지 확인 (실제 KIS API 에러 메시지가 여기 들어있음)
        String detail = e.getDetail();

        boolean inMsg = msg != null && (msg.contains("EGW00123") || msg.contains("만료된 token") || msg.contains("expired"));
        boolean inDetail = detail != null && (detail.contains("EGW00123") || detail.contains("만료된 token") || detail.contains("expired"));

        return inMsg || inDetail;
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
            // [변경됨] Body를 JSON 문자열로 수동 직렬화
            String jsonBody = null;
            if (body != null) {
                try {
                    // 이미 String이면 그대로, 객체면 JSON String으로 변환
                    if (body instanceof String) {
                        jsonBody = (String) body;
                    } else {
                        jsonBody = objectMapper.writeValueAsString(body);
                    }

                    // 로깅 (SecureLogger는 String 입력도 처리 가능)
                    log.debug("Request Body: {}", secureLogger.maskSensitive(jsonBody));
                } catch (Exception e) {
                    log.warn("Request Body parsing failed: {}", e.getMessage());
                    // 변환 실패 시 toString()이라도 사용 (혹은 예외 던지기)
                    jsonBody = body.toString();
                }
            }

            log.debug("KIS API 호출: {} {}", method, uri);

            // [변경됨] HttpEntity에 변환된 String Body를 담음
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

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
            // msg1 뿐만 아니라 msg_cd도 함께 로깅에 포함하거나 반환
            // 여기서는 기존 로직 유지하되, 메시지에 코드가 포함되면 isTokenExpiredError에서 감지 가능
            if (body.contains("msg1")) {
                int start = body.indexOf("msg1") + 7;
                int end = body.indexOf("\"", start);
                String msg1 = (end > start) ? body.substring(start, end) : "";

                // msg_cd도 찾아서 붙여줌 (선택사항)
                if(body.contains("EGW00123")) {
                    return msg1 + " (EGW00123)";
                }
                return msg1;
            }
            return "KIS API 오류: " + e.getStatusCode();
        } catch (Exception ex) {
            return "KIS API 오류: " + e.getStatusCode();
        }
    }
}