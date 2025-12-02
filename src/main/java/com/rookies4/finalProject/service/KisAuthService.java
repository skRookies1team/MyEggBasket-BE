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
	 * KIS API 인증 토큰을 발급합니다.
	 * @param useVirtualServer 모의투자 서버 사용 여부
	 * @param user 사용자 엔티티
	 * @return KIS 토큰 응답
	 */
	public KisAuthTokenDTO.KisTokenResponse issueToken(boolean useVirtualServer, User user) {

        String path = "/oauth2/tokenP";

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
		URI uri = KisApiConfig.uri(useVirtualServer, path);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		// 인코딩된 appkey와 appsecret을 디코딩
		String decodedAppkey = KisApiConfig.decodeBase64(user.getAppkey());
		String decodedAppsecret = KisApiConfig.decodeBase64(user.getAppsecret());

		Map<String, String> payload = Map.of(
				"grant_type", "client_credentials",
				"appkey", decodedAppkey,
				"appsecret", decodedAppsecret);

		HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

		try {
			log.info("KIS 토큰 발급 요청: URI={}, Virtual={}", uri, useVirtualServer);
			ResponseEntity<KisAuthTokenDTO.KisTokenResponse> response =
					restTemplate.exchange(uri, HttpMethod.POST, request, KisAuthTokenDTO.KisTokenResponse.class);

			KisAuthTokenDTO.KisTokenResponse body = response.getBody();
			if (body == null) {
				log.error("KIS 토큰 응답 본문이 비어있습니다.");
				throw new BusinessException(ErrorCode.KIS_TOKEN_ISSUANCE_FAILED,
						"KIS 인증 토큰 발급에 실패했습니다. 응답이 비어있습니다.");
			}

			log.info("KIS 토큰 발급 성공: TokenType={}, ExpiresIn={}", body.getTokenType(), body.getExpiresIn());
            KisAuthToken kisAuthToken = KisAuthToken.builder()
                    .user(user)
                    .accessToken(body.getAccessToken())
                    .tokenType(body.getTokenType())
                    .expiresIn(body.getExpiresIn())
                    .accessTokenTokenExpired(body.getAccessTokenExpired())
                    .build();
            kisAuthRepository.save(kisAuthToken);
            return body;

		} catch (RestClientResponseException e) {
			log.error("KIS 토큰 발급 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
			throw new BusinessException(ErrorCode.KIS_TOKEN_ISSUANCE_FAILED,
					String.format("KIS 인증 토큰 발급에 실패했습니다. [HTTP %s] %s",
							e.getStatusCode(), e.getResponseBodyAsString()));
		} catch (RestClientException e) {
			log.error("KIS API 호출 중 오류 발생: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.KIS_API_ERROR,
					"KIS API 호출 중 오류가 발생했습니다: " + e.getMessage());
		}
	}
}


