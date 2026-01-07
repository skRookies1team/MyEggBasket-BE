package com.rookies4.finalProject.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.KisAuthToken;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.KisAuthRepository;
import com.rookies4.finalProject.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisAuthService {
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final KisAuthRepository kisAuthRepository;
    private final EncryptionUtil encryptionUtil;

    // 인메모리 토큰 캐시 (Key: UserId, Value: TokenInfo)
    // DB 조회 부하를 줄이기 위해 사용
    private final Map<Long, CachedTokenInfo> tokenCache = new ConcurrentHashMap<>();

    private record CachedTokenInfo(KisAuthTokenDTO.KisTokenResponse response, LocalDateTime expirationTime) {
        boolean isValid() {
            // 만료 5분 전까지를 유효한 것으로 간주
            return LocalDateTime.now().plusMinutes(5).isBefore(expirationTime);
        }
    }

    /**
     * REST API용 accessToken
     */
    public KisAuthTokenDTO.KisTokenResponse issueToken(boolean useVirtualServer, User user) {
        // 1. 메모리 캐시 확인 (DB 부하 방지)
        if (tokenCache.containsKey(user.getId())) {
            CachedTokenInfo cached = tokenCache.get(user.getId());
            if (cached.isValid()) {
                if (log.isDebugEnabled()) {
                    log.debug("[KIS Auth] 메모리 캐시 토큰 사용 - userId: {}", user.getId());
                }
                return cached.response();
            } else {
                // 만료된 경우 캐시 제거
                tokenCache.remove(user.getId());
            }
        }

        // 2. DB 조회 및 갱신 로직 (기존 로직 유지하되 캐시 업데이트 추가)
        return kisAuthRepository.findByUser(user)
                .filter(token -> !isTokenExpired(token))
                .map(token -> {
                    if (log.isDebugEnabled()) {
                        log.debug("[KIS Auth] DB 토큰 재사용 - userId: {}", user.getId());
                    }
                    KisAuthTokenDTO.KisTokenResponse response = KisAuthTokenDTO.KisTokenResponse.fromEntity(token);

                    // DB에서 가져온 유효 토큰을 메모리 캐시에 등록
                    tokenCache.put(user.getId(), new CachedTokenInfo(response, token.getAccessTokenTokenExpired()));

                    return response;
                })
                .orElseGet(() -> {
                    log.info("[KIS Auth] 신규 토큰 발급 (API 요청) - userId: {}", user.getId());
                    KisAuthTokenDTO.KisTokenRequest tokenRequest = buildTokenRequest(user);
                    KisAuthTokenDTO.KisTokenResponse response = requestNewToken(useVirtualServer, tokenRequest);

                    // DB 저장 및 캐시 등록
                    saveOrUpdateToken(user, response);

                    return response;
                });
    }

    /**
     * WebSocket용 approval_key (항상 신규 발급)
     */
    public String issueApprovalKey(boolean useVirtualServer, User user) {
        log.info("[KIS Auth] 웹소켓 접속키 신규 발급 - userId: {}", user.getId());
        return reissueApprovalKey(useVirtualServer, user);
    }

    /**
     * WebSocket approval_key 강제 재발급
     */
    public String reissueApprovalKey(boolean useVirtualServer, User user) {
        KisAuthToken token = kisAuthRepository.findByUser(user)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.AUTH_TOKEN_NOT_FOUND,
                                "인증 토큰 정보가 없습니다.")
                );

        KisAuthTokenDTO.KisApprovalKeyResponse response =
                requestNewApprovalKey(useVirtualServer, user);

        token.setApprovalKey(response.getApprovalKey());
        kisAuthRepository.save(token);

        return response.getApprovalKey();
    }

    /**
     * 토큰 강제 만료
     */
    public void expireToken(User user) {
        // 메모리 캐시 즉시 제거
        tokenCache.remove(user.getId());

        kisAuthRepository.findByUser(user).ifPresent(token -> {
            log.info("[KIS Auth] 토큰 강제 만료 - userId: {}", user.getId());
            token.setAccessTokenTokenExpired(LocalDateTime.now().minusMinutes(1));
            kisAuthRepository.save(token);
        });
    }

    // ========== 내부 유틸 메서드 ==========

    private boolean isTokenExpired(KisAuthToken token) {
        return token.getAccessTokenTokenExpired()
                .isBefore(LocalDateTime.now().plusMinutes(5));
    }

    private KisAuthTokenDTO.KisTokenRequest buildTokenRequest(User user) {
        return KisAuthTokenDTO.KisTokenRequest.builder()
                .grant_type("client_credentials")
                .appkey(encryptionUtil.decrypt(user.getAppkey()))
                .appsecret(encryptionUtil.decrypt(user.getAppsecret()))
                .build();
    }

    private KisAuthTokenDTO.KisTokenResponse requestNewToken(
            boolean useVirtualServer,
            KisAuthTokenDTO.KisTokenRequest request
    ) {
        try {
            return restTemplate.postForObject(
                    KisApiConfig.tokenUrl(useVirtualServer),
                    request,
                    KisAuthTokenDTO.KisTokenResponse.class
            );
        } catch (RestClientException e) {
            log.error("[KIS Auth] 토큰 발급 실패: {}", e.getMessage());
            throw new BusinessException(
                    ErrorCode.KIS_API_ERROR,
                    "토큰 발급에 실패했습니다."
            );
        }
    }

    private KisAuthTokenDTO.KisApprovalKeyResponse requestNewApprovalKey(
            boolean useVirtualServer,
            User user
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String decryptedAppkey = encryptionUtil.decrypt(user.getAppkey());
        String decryptedAppsecret = encryptionUtil.decrypt(user.getAppsecret());

        Map<String, String> requestBody = Map.of(
                "grant_type", "client_credentials",
                "appkey", decryptedAppkey,
                "secretkey", decryptedAppsecret
        );

        HttpEntity<Map<String, String>> requestEntity =
                new HttpEntity<>(requestBody, headers);

        try {
            return restTemplate.postForObject(
                    KisApiConfig.approvalUrl(useVirtualServer),
                    requestEntity,
                    KisAuthTokenDTO.KisApprovalKeyResponse.class
            );
        } catch (RestClientException e) {
            log.error("[KIS Auth] 웹소켓 접속키 발급 실패: {}", e.getMessage());
            throw new BusinessException(
                    ErrorCode.KIS_API_ERROR,
                    "웹소켓 접속키 발급에 실패했습니다."
            );
        }
    }

    public String getHashKey(User user, String jsonBody) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("appkey", encryptionUtil.decrypt(user.getAppkey()));
            headers.set("appsecret", encryptionUtil.decrypt(user.getAppsecret()));
            headers.set("User-Agent", "Mozilla/5.0");

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            String url = "https://openapi.koreainvestment.com:9443/uapi/hashkey";

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("HASH")) {
                return (String) body.get("HASH");
            }

            log.error("[KIS Auth] HashKey 응답에 HASH 값이 없음");
            return null;

        } catch (Exception e) {
            log.error("[KIS Auth] HashKey 발급 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "HashKey 발급 중 오류 발생");
        }
    }

    private void saveOrUpdateToken(User user, KisAuthTokenDTO.KisTokenResponse response) {
        KisAuthToken token = kisAuthRepository.findByUser(user)
                .orElse(KisAuthToken.builder().user(user).build());

        token.updateToken(
                response.getAccessToken(),
                response.getTokenType(),
                response.getExpiresIn()
        );

        kisAuthRepository.save(token);

        // 캐시에도 저장 (중요: Entity가 업데이트된 후의 만료 시간을 사용해야 정확함)
        // updateToken 내부 로직에 따라 만료 시간이 설정되었으므로, token 객체에서 시간 정보를 가져옴
        tokenCache.put(user.getId(), new CachedTokenInfo(response, token.getAccessTokenTokenExpired()));
    }
}