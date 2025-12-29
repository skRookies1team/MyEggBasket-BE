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

@Slf4j
@Service
@RequiredArgsConstructor
public class KisAuthService {
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final KisAuthRepository kisAuthRepository;
    private final EncryptionUtil encryptionUtil;

    /**
     * REST API용 accessToken
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KisAuthTokenDTO.KisTokenResponse issueToken(boolean useVirtualServer, User user) {
        return kisAuthRepository.findByUser(user)
                .filter(token -> !isTokenExpired(token))
                .map(token -> {
                    // 재사용 시에는 DEBUG 레벨로만 로깅
                    if (log.isDebugEnabled()) {
                        log.debug("[KIS Auth] 토큰 재사용 - userId: {}", user.getId());
                    }
                    return KisAuthTokenDTO.KisTokenResponse.fromEntity(token);
                })
                .orElseGet(() -> {
                    log.info("[KIS Auth] 신규 토큰 발급 - userId: {}", user.getId());
                    KisAuthTokenDTO.KisTokenRequest tokenRequest = buildTokenRequest(user);
                    KisAuthTokenDTO.KisTokenResponse response =
                            requestNewToken(useVirtualServer, tokenRequest);
                    saveOrUpdateToken(user, response);
                    return response;
                });
    }

    /**
     * WebSocket용 approval_key (항상 신규 발급)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issueApprovalKey(boolean useVirtualServer, User user) {
        log.info("[KIS Auth] 웹소켓 접속키 신규 발급 - userId: {}", user.getId());
        return reissueApprovalKey(useVirtualServer, user);
    }

    /**
     * WebSocket approval_key 강제 재발급
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireToken(User user) {
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
    }
}