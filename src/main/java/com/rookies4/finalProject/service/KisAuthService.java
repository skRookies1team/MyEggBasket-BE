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

    @Transactional(propagation = Propagation.REQUIRES_NEW) // 트랜잭션 전파 설정 추가
    public KisAuthTokenDTO.KisTokenResponse issueToken(boolean useVirtualServer, User user) {
        return kisAuthRepository.findByUser(user)
                .filter(token -> !isTokenExpired(token))
                .map(token -> {
                    log.info("기존 KIS 토큰 재사용: userId={}, expiresAt={}", user.getId(), token.getAccessTokenTokenExpired());
                    return KisAuthTokenDTO.KisTokenResponse.fromEntity(token);
                })
                .orElseGet(() -> {
                    log.info("신규 KIS 토큰 발급: userId={}", user.getId());
                    KisAuthTokenDTO.KisTokenRequest tokenRequest = buildTokenRequest(user);
                    KisAuthTokenDTO.KisTokenResponse response = requestNewToken(useVirtualServer, tokenRequest);
                    saveOrUpdateToken(user, response);
                    return response;
                });
    }
    // [추가] 토큰 강제 만료 처리 메서드
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireToken(User user) {
        kisAuthRepository.findByUser(user).ifPresent(token -> {
            log.info("KIS 토큰 강제 만료 처리: userId={}", user.getId());
            // 만료 시간을 현재 시간보다 과거로 설정하여 다음 요청 시 재발급 유도
            token.setAccessTokenTokenExpired(LocalDateTime.now().minusMinutes(1));
            kisAuthRepository.save(token);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW) // 트랜잭션 전파 설정 추가
    public String issueApprovalKey(boolean useVirtualServer, User user) {
        KisAuthToken token = kisAuthRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_NOT_FOUND, "인증 토큰 정보가 없습니다."));

        if (token.getApprovalKey() != null) {
            log.info("기존 웹소켓 접속키 재사용: userId={}", user.getId());
            return token.getApprovalKey();
        }

        log.info("신규 웹소켓 접속키 발급: userId={}", user.getId());
        KisAuthTokenDTO.KisApprovalKeyResponse response = requestNewApprovalKey(useVirtualServer, user);
        token.setApprovalKey(response.getApprovalKey());
        kisAuthRepository.save(token);
        return response.getApprovalKey();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String reissueApprovalKey(boolean useVirtualServer, User user) {
        KisAuthToken token = kisAuthRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_NOT_FOUND, "인증 토큰 정보가 없습니다."));

        log.info("웹소켓 접속키 강제 재발급: userId={}", user.getId());

        KisAuthTokenDTO.KisApprovalKeyResponse response = requestNewApprovalKey(useVirtualServer, user);

        token.setApprovalKey(response.getApprovalKey());
        kisAuthRepository.save(token);

        return response.getApprovalKey();
    }

    private boolean isTokenExpired(KisAuthToken token) {
        return token.getAccessTokenTokenExpired().isBefore(LocalDateTime.now().plusMinutes(5));
    }

    private KisAuthTokenDTO.KisTokenRequest buildTokenRequest(User user) {
        return KisAuthTokenDTO.KisTokenRequest.builder()
                .grant_type("client_credentials")
                .appkey(encryptionUtil.decrypt(user.getAppkey()))
                .appsecret(encryptionUtil.decrypt(user.getAppsecret()))
                .build();
    }

    private KisAuthTokenDTO.KisTokenResponse requestNewToken(boolean useVirtualServer, KisAuthTokenDTO.KisTokenRequest request) {
        try {
            return restTemplate.postForObject(
                    KisApiConfig.tokenUrl(useVirtualServer),
                    request,
                    KisAuthTokenDTO.KisTokenResponse.class
            );
        } catch (RestClientException e) {
            log.error("KIS 토큰 발급 API 호출 실패", e);
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "토큰 발급에 실패했습니다.");
        }
    }

    private KisAuthTokenDTO.KisApprovalKeyResponse requestNewApprovalKey(boolean useVirtualServer, User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String decryptedAppkey = encryptionUtil.decrypt(user.getAppkey());
        String decryptedAppsecret = encryptionUtil.decrypt(user.getAppsecret());

        Map<String, String> requestBody = Map.of(
                "grant_type", "client_credentials",
                "appkey", decryptedAppkey,
                "secretkey", decryptedAppsecret  
        );

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            return restTemplate.postForObject(
                    KisApiConfig.approvalUrl(useVirtualServer),
                    requestEntity,
                    KisAuthTokenDTO.KisApprovalKeyResponse.class
            );
        } catch (RestClientException e) {
            log.error("KIS 웹소켓 접속키 발급 API 호출 실패", e);
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "웹소켓 접속키 발급에 실패했습니다.");
        }
    }
    public String getHashKey(User user, String jsonBody) {
        try {
            // 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("appkey", encryptionUtil.decrypt(user.getAppkey()));
            headers.set("appsecret", encryptionUtil.decrypt(user.getAppsecret()));
            headers.set("User-Agent", "Mozilla/5.0"); // 필수는 아니지만 안정성을 위해 권장

            // Body는 이미 JSON 문자열 상태여야 함 (KisApiClient에서 변환해서 넘겨줌)
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            // 실전/모의 서버 구분 없이 HashKey 발급은 실전 URL 사용 권장 (또는 설정에 따름)
            // 여기서는 안전하게 실전 URL로 고정하거나 Config에서 가져오세요.
            String url = "https://openapi.koreainvestment.com:9443/uapi/hashkey";

            // 요청 전송
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("HASH")) {
                return (String) body.get("HASH");
            }

            log.error("HashKey 응답에 HASH 값이 없습니다: {}", body);
            return null;

        } catch (Exception e) {
            log.error("HashKey 발급 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "HashKey 발급 중 오류 발생");
        }
    }
    private void saveOrUpdateToken(User user, KisAuthTokenDTO.KisTokenResponse response) {
        KisAuthToken token = kisAuthRepository.findByUser(user)
                .orElse(KisAuthToken.builder().user(user).build());

        token.updateToken(
                response.getAccess_token(),
                response.getToken_type(),
                response.getExpires_in()
        );
        kisAuthRepository.save(token);
    }
}