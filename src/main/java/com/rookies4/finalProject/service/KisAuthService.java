package com.rookies4.finalProject.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.KisAuthToken;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.KisAuthRepository;
import com.rookies4.finalProject.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

/**
 * Handles authentication against the Korea Investment & Securities Open API.
 */
@Service
@Transactional
public class KisAuthService {

    private static final Logger log = LoggerFactory.getLogger(KisAuthService.class);

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final KisAuthRepository kisAuthRepository;

    public KisAuthService(RestTemplateBuilder restTemplateBuilder,
                          UserRepository userRepository, KisAuthRepository kisAuthRepository) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.userRepository = userRepository;
        this.kisAuthRepository = kisAuthRepository;
    }

    /**
     * KIS 토큰 정보를 DB에 저장하거나 갱신합니다. (생성 및 업데이트 로직)
     * @param user 사용자 엔티티
     * @param newToken 새로 발급받은 토큰 DTO
     * @return 갱신되거나 새로 생성된 KisAuthToken 엔티티
     */
    @Transactional
    public KisAuthToken renewToken(User user, KisAuthTokenDTO.KisTokenResponse newToken) { //
        KisAuthToken existingToken = kisAuthRepository.findByUser(user) //
                .orElse(KisAuthToken.builder()
                        .user(user)
                        .tokenType(null)
                        .accessToken(null)
                        .accessTokenTokenExpired(null)
                        .expiresIn(null)
                        .build()); // 없으면 새로 생성

        // 💡 [수정] 모든 토큰 필드를 업데이트합니다.
        existingToken.setAccessToken(newToken.getAccessToken());
        existingToken.setTokenType(newToken.getTokenType());
        existingToken.setExpiresIn(newToken.getExpiresIn());
        existingToken.setAccessTokenTokenExpired(newToken.getAccessTokenExpired());

        return kisAuthRepository.save(existingToken); // 기존 레코드 업데이트 또는 새 레코드 저장
    }
    @Transactional
    public KisAuthToken renewApprovalKey(User user, KisAuthTokenDTO.KisApprovalKeyResponse newApprovalKey) { //
        KisAuthToken existingToken = kisAuthRepository.findByUser(user) //
                .orElse(KisAuthToken.builder()
                        .user(user)
                        .tokenType(null)
                        .accessToken(null)
                        .accessTokenTokenExpired(null)
                        .approvalKey(null)
                        .expiresIn(null)
                        .build()); // 없으면 새로 생성

        // 💡 [수정] 모든 토큰 필드를 업데이트합니다.
        existingToken.setApprovalKey(newApprovalKey.getApprovalKey());


        return kisAuthRepository.save(existingToken); // 기존 레코드 업데이트 또는 새 레코드 저장
    }


    /**
     * KIS API 인증 토큰을 발급합니다.
     * @param useVirtualServer 모의투자 서버 사용 여부
     * @param user 사용자 엔티티
     * @return KIS 토큰 응답
     */
    public KisAuthTokenDTO.KisTokenResponse issueToken(boolean useVirtualServer, User user) { //

        String path = "/oauth2/tokenP";

        // 사용자 검증
        if (user == null) { //
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자 정보를 찾을 수 없습니다."); //
        }

        // API 키 검증
        if (!StringUtils.hasText(user.getAppkey())) { //
            throw new BusinessException(ErrorCode.KIS_API_KEY_NOT_FOUND,
                    "KIS API 키가 설정되지 않았습니다. 사용자 설정에서 API 키를 등록해주세요."); //
        }

        if (!StringUtils.hasText(user.getAppsecret())) { //
            throw new BusinessException(ErrorCode.KIS_API_SECRET_NOT_FOUND,
                    "KIS API Secret이 설정되지 않았습니다. 사용자 설정에서 API Secret을 등록해주세요."); //
        }
        URI uri = KisApiConfig.uri(useVirtualServer, path); //

        HttpHeaders headers = new HttpHeaders(); //
        headers.setContentType(MediaType.APPLICATION_JSON); //

        // 인코딩된 appkey와 appsecret을 디코딩
        String decodedAppkey = KisApiConfig.decodeBase64(user.getAppkey()); //
        String decodedAppsecret = KisApiConfig.decodeBase64(user.getAppsecret()); //

        Map<String, String> payload = Map.of( //
                "grant_type", "client_credentials",
                "appkey", decodedAppkey,
                "appsecret", decodedAppsecret);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers); //

        try {
            log.info("KIS 토큰 발급 요청: URI={}, Virtual={}", uri, useVirtualServer); //
            ResponseEntity<KisAuthTokenDTO.KisTokenResponse> response =
                    restTemplate.exchange(uri, HttpMethod.POST, request, KisAuthTokenDTO.KisTokenResponse.class); //

            KisAuthTokenDTO.KisTokenResponse body = response.getBody(); //
            if (body == null) { //
                log.error("KIS 토큰 응답 본문이 비어있습니다."); //
                throw new BusinessException(ErrorCode.KIS_TOKEN_ISSUANCE_FAILED,
                        "KIS 인증 토큰 발급에 실패했습니다. 응답이 비어있습니다."); //
            }

            log.info("KIS 토큰 발급 성공: TokenType={}, ExpiresIn={}", body.getTokenType(), body.getExpiresIn());

            // 💡 [수정] 토큰 엔티티 생성 및 DB 저장 로직을 renewToken 메서드로 위임
            renewToken(user, body);

            return body; //

        } catch (RestClientResponseException e) {
            log.error("KIS 토큰 발급 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString(), e); //
            throw new BusinessException(ErrorCode.KIS_TOKEN_ISSUANCE_FAILED,
                    String.format("KIS 인증 토큰 발급에 실패했습니다. [HTTP %s] %s",
                            e.getStatusCode(), e.getResponseBodyAsString())); //
        } catch (RestClientException e) {
            log.error("KIS API 호출 중 오류 발생: {}", e.getMessage(), e); //
            throw new BusinessException(ErrorCode.KIS_API_ERROR,
                    "KIS API 호출 중 오류가 발생했습니다: " + e.getMessage()); //
        }
    }

    /**
     * KIS API Websocket approvalKey를 발급합니다.
     * @param user 사용자 엔티티
     * @return KIS approvalKey 응답
     */
    public KisAuthTokenDTO.KisApprovalKeyResponse issueApprovalKey(boolean useVirtualServer, User user){

        String path = "/oauth2/Approval";

        // 사용자 검증
        if (user == null) { //
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
        }

        // API 키 검증
        if (!StringUtils.hasText(user.getAppkey())) {
            throw new BusinessException(ErrorCode.KIS_API_KEY_NOT_FOUND,
                    "KIS API 키가 설정되지 않았습니다. 사용자 설정에서 API 키를 등록해주세요.");
        }

        if (!StringUtils.hasText(user.getAppsecret())) {
            throw new BusinessException(ErrorCode.KIS_API_SECRET_NOT_FOUND,
                    "KIS API Secret이 설정되지 않았습니다. 사용자 설정에서 API Secret을 등록해주세요.");
        }
        URI uri = KisApiConfig.uri(useVirtualServer, path);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 인코딩된 appkey와 appsecret을 디코딩
        String decodedAppkey = KisApiConfig.decodeBase64(user.getAppkey());
        String decodedAppsecret = KisApiConfig.decodeBase64(user.getAppsecret());

        Map<String, String> payload = Map.of(
                "grant_type", "client_credentials",
                "appkey", decodedAppkey,
                "secretkey", decodedAppsecret);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<KisAuthTokenDTO.KisApprovalKeyResponse> response =
                    restTemplate.exchange(uri, HttpMethod.POST, request, KisAuthTokenDTO.KisApprovalKeyResponse.class); //

            KisAuthTokenDTO.KisApprovalKeyResponse body = response.getBody(); //
            if (body == null) { //
                throw new BusinessException(ErrorCode.KIS_TOKEN_ISSUANCE_FAILED,
                        "KIS 인증 토큰 발급에 실패했습니다. 응답이 비어있습니다."); //
            }

            renewApprovalKey(user, body);

            return body; //

        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.KIS_TOKEN_ISSUANCE_FAILED,
                    String.format("KIS 인증 토큰 발급에 실패했습니다. [HTTP %s] %s",
                            e.getStatusCode(), e.getResponseBodyAsString())); //
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR,
                    "KIS API 호출 중 오류가 발생했습니다: " + e.getMessage()); //
        }
    }
}