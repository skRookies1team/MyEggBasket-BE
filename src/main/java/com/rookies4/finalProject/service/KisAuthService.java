package com.rookies4.finalProject.service;

import com.rookies4.finalProject.dto.KisAuthTokenDTO.KisTokenResponse;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
     * @param user     사용자 엔티티
     * @param newToken 새로 발급받은 토큰 DTO
     * @return 갱신되거나 새로 생성된 KisAuthToken 엔티티
     */
    @Transactional
    public KisAuthToken renewToken(User user, KisAuthTokenDTO.KisTokenResponse newToken) {
        KisAuthToken existingToken = kisAuthRepository.findByUser(user)
                .orElse(KisAuthToken.builder()
                        .user(user)
                        .tokenType(null)
                        .accessToken(null)
                        .accessTokenTokenExpired(null)
                        .expiresIn(null)
                        .build()); // 없으면 새로 생성

        existingToken.setAccessToken(newToken.getAccessToken());
        existingToken.setTokenType(newToken.getTokenType());
        existingToken.setExpiresIn(newToken.getExpiresIn());
        existingToken.setAccessTokenTokenExpired(newToken.getAccessTokenExpired());

        return kisAuthRepository.save(existingToken);
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


        return kisAuthRepository.save(existingToken);
    }

    /**
     * KIS API 인증 토큰을 발급합니다.
     * DB에 저장된 토큰이 있으면 재사용하고, 없거나 만료되었다면 새로 발급해서 저장한 뒤 반환합니다.
     * @param useVirtualServer 모의투자 서버 사용 여부
     * @param user             사용자 엔티티
     * @return KIS 토큰 응답
     */
    public KisAuthTokenDTO.KisTokenResponse issueToken(boolean useVirtualServer, User user) { //

        // 사용자 검증
        if (user == null) {
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

        // 기존 토큰 조회 & 유효하면 재사용
        KisAuthToken existing = kisAuthRepository.findByUser(user).orElse(null);
        if (existing != null && isTokenValid(existing)) {
            log.info("기존 KIS 토큰 재사용: userId={}, expiresAt={}",
                    user.getId(), existing.getAccessTokenTokenExpired());

            KisAuthTokenDTO.KisTokenResponse dto = new KisTokenResponse();
            dto.setAccessToken(existing.getAccessToken());
            dto.setTokenType(existing.getTokenType());
            dto.setExpiresIn(existing.getExpiresIn());
            dto.setAccessTokenExpired(existing.getAccessTokenTokenExpired());

            return dto;
        }

        // 유효한 토큰이 없으면 새로 발급
        KisAuthTokenDTO.KisTokenResponse body =
                requestNewTokenFromKis(useVirtualServer, user);

        // DB에 저장/갱신
        renewToken(user, body);

        return body;
    }

    /**
     * 실제 KIS 서버에 토큰 발급을 요청하는 메서드 항상 새 토큰을 발급받음
     */
    private KisAuthTokenDTO.KisTokenResponse requestNewTokenFromKis(boolean useVirtualServer, User user) {

        String path = "/oauth2/tokenP";
        URI uri = KisApiConfig.uri(useVirtualServer, path);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 인코딩된 appkey와 appsecret을 디코딩
        String decodedAppkey = KisApiConfig.decodeBase64(user.getAppkey());
        String decodedAppsecret = KisApiConfig.decodeBase64(user.getAppsecret());

        Map<String, String> payload = Map.of(
                "grant_type", "client_credentials",
                "appkey", decodedAppkey,
                "appsecret", decodedAppsecret
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

        try {
            log.info("KIS 토큰 발급 요청: URI={}, Virtual={}", uri, useVirtualServer);
            ResponseEntity<KisAuthTokenDTO.KisTokenResponse> response =
                    restTemplate.exchange(
                            uri,
                            HttpMethod.POST,
                            request,
                            KisAuthTokenDTO.KisTokenResponse.class
                    );

            KisAuthTokenDTO.KisTokenResponse body = response.getBody();
            if (body == null) {
                log.error("KIS 토큰 응답 본문이 비어있습니다.");
                throw new BusinessException(
                        ErrorCode.KIS_TOKEN_ISSUANCE_FAILED,
                        "KIS 인증 토큰 발급에 실패했습니다. 응답이 비어있습니다."
                );
            }

            log.info("KIS 토큰 발급 성공: TokenType={}, ExpiresIn={}",
                    body.getTokenType(), body.getExpiresIn());

            return body;

        } catch (RestClientResponseException e) {
            log.error("KIS 토큰 발급 실패 (HTTP {}): {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(
                    ErrorCode.KIS_TOKEN_ISSUANCE_FAILED,
                    String.format("KIS 인증 토큰 발급에 실패했습니다. [HTTP %s] %s",
                            e.getStatusCode(), e.getResponseBodyAsString())
            );
        } catch (RestClientException e) {
            log.error("KIS API 호출 중 오류 발생: {}", e.getMessage(), e);
            throw new BusinessException(
                    ErrorCode.KIS_API_ERROR,
                    "KIS API 호출 중 오류가 발생했습니다: " + e.getMessage()
            );
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

    /**
     * DB에 저장된 토큰이 유효한지 검사 만료 시간이 1분 이상 남아있으면 유효하다고 본다.
     */
    private boolean isTokenValid(KisAuthToken token) {
        if (!StringUtils.hasText(token.getAccessToken())
                || token.getAccessTokenTokenExpired() == null) {
            return false;
        }

        try {
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime expiredAt =
                    LocalDateTime.parse(token.getAccessTokenTokenExpired(), formatter);

            LocalDateTime now = LocalDateTime.now();

            // 만료 60초 전까지만 유효로 본다
            return now.isBefore(expiredAt.minusSeconds(60));

        } catch (Exception e) {
            log.warn("KIS 토큰 만료 시간 파싱 실패, 재발급 시도. value={}",
                    token.getAccessTokenTokenExpired());
            return false;
        }
    }
}