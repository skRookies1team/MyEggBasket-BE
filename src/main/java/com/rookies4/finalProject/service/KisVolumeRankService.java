package com.rookies4.finalProject.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.component.SecureLogger;
import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.KisAuthToken;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisVolumeRankDTO;
import com.rookies4.finalProject.dto.VolumeRankResponseDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.KisAuthRepository;
import com.rookies4.finalProject.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KisVolumeRankService {

    private static final Logger log = LoggerFactory.getLogger(KisVolumeRankService.class);

    private final RestTemplate restTemplate;
    private final KisAuthRepository kisAuthRepository;
    private final ObjectMapper objectMapper; // JSON 로깅용
    private final SecureLogger secureLogger; // 민감 정보 마스킹용

    /**
     * 거래량 순위 TOP 10 조회 후 프론트엔드용 DTO로 변환하여 반환
     * @param user 사용자 정보
     * @return 거래량 순위 DTO 리스트
     */
    public List<VolumeRankResponseDTO> getVolumeRank(User user) {
        // API 경로
        String path = "/uapi/domestic-stock/v1/quotations/volume-rank";

        // 인증 토큰 조회
        KisAuthToken kisAuthToken = kisAuthRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "인증 토큰이 존재하지 않습니다. 먼저 토큰을 발급받아주세요."
                ));

        String decodedAppkey = EncryptionUtil.decrypt(user.getAppkey());
        String decodedAppsecret = EncryptionUtil.decrypt(user.getAppsecret());
        String tradeId = "FHPST01710000"; // 거래량 순위 조회 TR_ID

        // Request Header 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("content-type", "application/json; charset=utf-8");
        headers.set("authorization", kisAuthToken.getTokenType() + " " + kisAuthToken.getAccessToken());
        headers.set("appkey", decodedAppkey);
        headers.set("appsecret", decodedAppsecret);
        headers.set("tr_id", tradeId);
        headers.set("custtype", "P"); // 개인

        // Query Parameter 설정
        Map<String, String> params = new HashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", "J"); // 주식 전체
        params.put("FID_COND_SCR_DIV_CODE", "20171"); // 거래량 순위
        params.put("FID_INPUT_ISCD", "0000"); // 전체
        params.put("FID_DIV_CLS_CODE", "0"); // 전체
        params.put("FID_BLNG_CLS_CODE", "0"); // 평균거래량
        params.put("FID_TRGT_CLS_CODE", "111111111"); // 전체 (보통주+우선주)
        params.put("FID_TRGT_EXLS_CLS_CODE", "000000"); // 제외 없음
        params.put("FID_INPUT_PRICE_1", ""); // 가격 조건 없음
        params.put("FID_INPUT_PRICE_2", ""); // 가격 조건 없음
        params.put("FID_VOL_CNT", ""); // 거래량 수 조건 없음
        params.put("FID_INPUT_DATE_1", ""); // 날짜 조건 없음

        HttpEntity<Void> request = new HttpEntity<>(headers);
        URI uriWithParams = KisApiConfig.uri(false, path, params);

        log.info("KIS 거래량 순위 조회 요청: userId={}", user.getId());

        try {
            // API 호출
            ResponseEntity<KisVolumeRankDTO.KisVolumeRankResponse> response =
                    restTemplate.exchange(
                            uriWithParams,
                            HttpMethod.GET,
                            request,
                            KisVolumeRankDTO.KisVolumeRankResponse.class
                    );

            KisVolumeRankDTO.KisVolumeRankResponse body = response.getBody();
            
            // [디버깅] API 응답 로그 출력
            try {
                log.info("KIS Volume Rank API Response: {}", secureLogger.maskSensitiveJson(body));
            } catch (Exception e) {
                log.error("Failed to log API response", e);
            }

            if (body == null || body.getOutput() == null) {
                throw new BusinessException(
                        ErrorCode.KIS_API_ERROR,
                        "거래량 순위 조회 응답이 비어있습니다."
                );
            }

            // 성공 여부 확인
            if (!"0".equals(body.getRtCd())) {
                log.error("KIS 거래량 순위 조회 실패: rt_cd={}, msg={}",
                        body.getRtCd(), body.getMsg1());
                throw new BusinessException(
                        ErrorCode.KIS_API_ERROR,
                        "거래량 순위 조회 실패: " + body.getMsg1()
                );
            }

            // TOP 10만 추출하여 프론트엔드용 DTO로 변환
            List<VolumeRankResponseDTO> result = body.getOutput().stream()
                    .limit(10)
                    .map(this::transformToResponseDTO)
                    .collect(Collectors.toList());

            log.info("KIS 거래량 순위 조회 및 변환 성공: userId={}, count={}",
                    user.getId(), result.size());

            return result;

        } catch (RestClientResponseException e) {
            log.error("KIS API 호출 실패 (HTTP {}): 요청 URI: {}, 응답 Body: {}",
                    e.getStatusCode(), uriWithParams, e.getResponseBodyAsString(), e);
            throw new BusinessException(
                    ErrorCode.KIS_API_ERROR,
                    String.format("거래량 순위 조회 실패. [HTTP %s] %s",
                            e.getStatusCode(), e.getResponseBodyAsString())
            );
        } catch (RestClientException e) {
            log.error("KIS API 호출 중 오류 발생: {}", e.getMessage(), e);
            throw new BusinessException(
                    ErrorCode.KIS_API_ERROR,
                    "거래량 순위 조회 중 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }

    /**
     * KIS API 원본 아이템을 프론트엔드 DTO로 변환하는 헬퍼 메소드
     */
    private VolumeRankResponseDTO transformToResponseDTO(KisVolumeRankDTO.VolumeRankItem item) {
        return VolumeRankResponseDTO.builder()
                .rank(Integer.parseInt(item.getRank()))
                .code(item.getStockCode())
                .name(item.getStockName())
                .price(new BigDecimal(item.getCurrentPrice().replace(",", "")))
                .change(new BigDecimal(item.getPriceChange().replace(",", "")))
                .rate(Double.parseDouble(item.getChangeRate()))
                .volume(Long.parseLong(item.getVolume().replace(",", "")))
                .prevVolume(Long.parseLong(item.getPrevVolume().replace(",", "")))
                .turnover(Double.parseDouble(item.getVolInrt()))
                .build();
    }
}