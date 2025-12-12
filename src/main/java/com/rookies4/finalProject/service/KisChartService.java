package com.rookies4.finalProject.service;

import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.dto.KisChartDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KisChartService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final KisAuthService kisAuthService;
    private final ObjectMapper objectMapper;

    public KisChartDTO.ChartResponse getChartData(String stockCode, String period, Long userId) {
        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 토큰 발급
        KisAuthTokenDTO.KisTokenResponse tokenResponse = kisAuthService.issueToken(false, user);
        String accessToken = tokenResponse.getAccessToken();

        // 3. KIS API 호출
        String path = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";
        URI uri = KisApiConfig.uri(false, path);

        // 4. 기간별 파라미터 설정
        String periodCode = getPeriodCode(period);
        String startDate = getStartDate(period);
        String endDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri)
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", stockCode)
                .queryParam("FID_INPUT_DATE_1", startDate)
                .queryParam("FID_INPUT_DATE_2", endDate)
                .queryParam("FID_PERIOD_DIV_CODE", periodCode)
                .queryParam("FID_ORG_ADJ_PRC", "1"); // 오타 수정: ORGN -> ORG

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", decodeBase64(user.getAppkey()));
        headers.set("appsecret", decodeBase64(user.getAppsecret()));
        // TR_ID를 다시 올바른 값으로 복원
        headers.set("tr_id", "FHKST03010100"); 
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    requestEntity,
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            
            try {
                log.info("KIS Chart API Response: {}", objectMapper.writeValueAsString(body));
            } catch (Exception e) {
                log.error("Failed to log API response", e);
            }

            if (body == null || body.get("rt_cd") == null || ((String)body.get("rt_cd")).isEmpty()) {
                 throw new BusinessException(ErrorCode.KIS_API_ERROR, "KIS API로부터 유효하지 않은 응답을 받았습니다.");
            }

            if (!"0".equals(body.get("rt_cd"))) {
                String msg = (String) body.get("msg1");
                throw new BusinessException(ErrorCode.KIS_API_ERROR, "차트 데이터 조회 실패: " + msg);
            }

            List<Map<String, Object>> output2 = (List<Map<String, Object>>) body.get("output2");
            if (output2 == null) {
                return KisChartDTO.ChartResponse.builder()
                        .stockCode(stockCode)
                        .period(period)
                        .data(Collections.emptyList())
                        .build();
            }

            List<KisChartDTO.ChartData> chartData = output2.stream()
                    .map(this::transformToChartData)
                    .collect(Collectors.toList());

            return KisChartDTO.ChartResponse.builder()
                    .stockCode(stockCode)
                    .period(period)
                    .data(chartData)
                    .build();

        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "KIS API 호출 실패: " + e.getMessage());
        }
    }

    private KisChartDTO.ChartData transformToChartData(Map<String, Object> output) {
        return KisChartDTO.ChartData.builder()
                .time(formatApiDate((String) output.get("stck_bsop_date")))
                .price(parseLong(output.get("stck_clpr")))
                .volume(parseLong(output.get("acml_vol")))
                .build();
    }

    private String getPeriodCode(String period) {
        switch (period.toLowerCase()) {
            case "day": return "D";
            case "week": return "W";
            case "month": return "M";
            case "year": return "Y";
            default: throw new BusinessException(ErrorCode.VALIDATION_ERROR, "유효하지 않은 기간입니다: " + period);
        }
    }

    private String getStartDate(String period) {
        LocalDate now = LocalDate.now();
        LocalDate startDate;
        switch (period.toLowerCase()) {
            case "day":
                startDate = now.minusMonths(6);
                break;
            case "week":
                startDate = now.minusYears(2);
                break;
            case "month":
            case "year":
                startDate = now.minusYears(5);
                break;
            default:
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "유효하지 않은 기간입니다: " + period);
        }
        return startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private String formatApiDate(String dateStr) {
        if (dateStr == null || dateStr.length() != 8) return dateStr;
        return dateStr.substring(0, 4) + "-" + dateStr.substring(4, 6) + "-" + dateStr.substring(6, 8);
    }

    private Long parseLong(Object value) {
        if (value == null) return 0L;
        String strValue = String.valueOf(value).trim();
        if (strValue.isEmpty()) return 0L;
        return Long.parseLong(strValue.replaceAll(",", ""));
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Base64 디코딩 실패: {}", encoded, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "잘못된 형식의 인증 정보입니다.");
        }
    }
}