package com.rookies4.finalProject.service;

import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.dto.KisKoreaIndexDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KisKoreaIndexService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final KisAuthService kisAuthService;
    private final EncryptionUtil encryptionUtil;

    public KisKoreaIndexDTO.KisKoreaIndexResponse getKoreaIndex(String indexCode, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        KisAuthTokenDTO.KisTokenResponse tokenResponse = kisAuthService.issueToken(false, user);
        String accessToken = tokenResponse.getAccessToken();

        String path = "/uapi/domestic-stock/v1/quotations/inquire-index-price";
        URI uri = KisApiConfig.uri(false, path);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri)
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")  // J: 주요지수 (코스피, 코스닥 등)
                .queryParam("FID_INPUT_ISCD", indexCode);

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", encryptionUtil.decrypt(user.getAppkey()));
        headers.set("appsecret", encryptionUtil.decrypt(user.getAppsecret()));
        headers.set("tr_id", "FHPUP01700000");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<KisKoreaIndexDTO.KisKoreaIndexResponse> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    requestEntity,
                    KisKoreaIndexDTO.KisKoreaIndexResponse.class
            );

            if (response.getBody() == null || !"0".equals(response.getBody().getRtCd())) {
                String msg = response.getBody() != null ? response.getBody().getMsg1() : "응답이 없습니다.";
                throw new BusinessException(ErrorCode.KIS_API_ERROR, "국내 지수 조회 실패: " + msg);
            }

            return response.getBody();

        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "KIS API 호출 실패: " + e.getMessage());
        }
    }
}