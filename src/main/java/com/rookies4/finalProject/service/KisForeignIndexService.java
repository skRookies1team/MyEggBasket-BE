package com.rookies4.finalProject.service;

import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.dto.KisForeignIndexDTO;
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
public class KisForeignIndexService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final KisAuthService kisAuthService;

    public KisForeignIndexDTO.KisForeignIndexResponse getForeignIndex(String indexCode, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        KisAuthTokenDTO.KisTokenResponse tokenResponse = kisAuthService.issueToken(false, user);
        String accessToken = tokenResponse.getAccessToken();

        String path = "/uapi/overseas-price/v1/quotations/price";
        URI uri = KisApiConfig.uri(false, path);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri)
                .queryParam("AUTH", "")
                .queryParam("EXCD", getExchangeCode(indexCode))
                .queryParam("SYMB", indexCode);

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", EncryptionUtil.decrypt(user.getAppkey()));
        headers.set("appsecret", EncryptionUtil.decrypt(user.getAppsecret()));
        headers.set("tr_id", "HHDFS00000300");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<KisForeignIndexDTO.KisForeignIndexResponse> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    requestEntity,
                    KisForeignIndexDTO.KisForeignIndexResponse.class
            );

            if (response.getBody() == null || !"0".equals(response.getBody().getRtCd())) {
                String msg = response.getBody() != null ? response.getBody().getMsg1() : "응답이 없습니다.";
                throw new BusinessException(ErrorCode.KIS_API_ERROR, "해외 지수 조회 실패: " + msg);
            }

            return response.getBody();

        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "KIS API 호출 실패: " + e.getMessage());
        }
    }

    private String getExchangeCode(String indexCode) {
        // 해외 지수별 거래소 코드 매핑
        switch (indexCode) {
            case "DJI":
            case "NAS":
            case "SPI":
                return "NYS"; // 미국
            case "NII":
                return "TSE"; // 일본
            case "HSI":
                return "HKS"; // 홍콩
            default:
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 해외 지수 코드입니다.");
        }
    }
}