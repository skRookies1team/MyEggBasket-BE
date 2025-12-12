package com.rookies4.finalProject.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.dto.KisInvestorTrendDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.StockRepository;
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
import java.util.*;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KisInvestorTrendService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final KisAuthService kisAuthService;
    private final ObjectMapper objectMapper;

    /**
     * ==============================
     * 1️ 단일 종목 투자자 동향 조회
     * ==============================
     */
    public KisInvestorTrendDTO.InvestorTrendResponse getInvestorTrend(String stockCode, Long userId) {

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. KIS Access Token 발급 (실전 계좌)
        KisAuthTokenDTO.KisTokenResponse tokenResponse =
                kisAuthService.issueToken(false, user);
        String accessToken = tokenResponse.getAccessToken();

        // 3. KIS API 호출 준비
        String path = "/uapi/domestic-stock/v1/quotations/inquire-investor";
        URI uri = KisApiConfig.uri(false, path);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri)
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", stockCode);

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", decodeBase64(user.getAppkey()));
        headers.set("appsecret", decodeBase64(user.getAppsecret()));
        headers.set("tr_id", "FHKST01010900");
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

            // 디버깅 로그
            log.info("KIS Investor Trend API Response: {}",
                    objectMapper.writeValueAsString(body));

            if (body == null || !"0".equals(body.get("rt_cd"))) {
                throw new BusinessException(
                        ErrorCode.KIS_API_ERROR,
                        "투자자 동향 조회 실패: " + body
                );
            }

            List<Map<String, Object>> outputList =
                    (List<Map<String, Object>>) body.get("output");

            // 종목명 조회 (DB 우선)
            String stockName = stockRepository.findById(stockCode)
                    .map(Stock::getName)
                    .orElse(null);

            List<KisInvestorTrendDTO.InvestorInfo> investors = new ArrayList<>();

            if (outputList == null || outputList.isEmpty()) {
                // 데이터 없음 → 0으로 반환
                investors.add(new KisInvestorTrendDTO.InvestorInfo("개인", 0L, 0L));
                investors.add(new KisInvestorTrendDTO.InvestorInfo("외국인", 0L, 0L));
                investors.add(new KisInvestorTrendDTO.InvestorInfo("기관", 0L, 0L));
            } else {
                Map<String, Object> validOutput = outputList.get(0);

                // 0이 아닌 데이터 우선 선택
                for (Map<String, Object> output : outputList) {
                    if (parseLong(output.get("prsn_ntby_qty")) != 0 ||
                            parseLong(output.get("frgn_ntby_qty")) != 0 ||
                            parseLong(output.get("orgn_ntby_qty")) != 0) {
                        validOutput = output;
                        break;
                    }
                }

                // 종목명 fallback
                if (stockName == null && validOutput.containsKey("hts_kor_isnm")) {
                    stockName = String.valueOf(validOutput.get("hts_kor_isnm"));
                }

                investors.add(new KisInvestorTrendDTO.InvestorInfo(
                        "개인",
                        parseLong(validOutput.get("prsn_ntby_qty")),
                        parseLong(validOutput.get("prsn_ntby_tr_pbmn")) * 1_000_000
                ));

                investors.add(new KisInvestorTrendDTO.InvestorInfo(
                        "외국인",
                        parseLong(validOutput.get("frgn_ntby_qty")),
                        parseLong(validOutput.get("frgn_ntby_tr_pbmn")) * 1_000_000
                ));

                investors.add(new KisInvestorTrendDTO.InvestorInfo(
                        "기관",
                        parseLong(validOutput.get("orgn_ntby_qty")),
                        parseLong(validOutput.get("orgn_ntby_tr_pbmn")) * 1_000_000
                ));
            }

            return KisInvestorTrendDTO.InvestorTrendResponse.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .investors(investors)
                    .build();

        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.KIS_API_ERROR,
                    "KIS 투자자 동향 조회 실패: " + e.getMessage()
            );
        }
    }

    /**
     * ==================================
     * 2️ 주요 종목(시장) 투자자 동향 조회
     * ==================================
     */
    public List<KisInvestorTrendDTO.InvestorTrendResponse>
    getMarketInvestorTrend(Long userId) {

        List<String> tickers = List.of(
                "005930", "000660", "207940", "005380", "000270",
                "055550", "105560", "068270", "015760", "028260",
                "032830", "012330", "035420", "006400", "086790",
                "006405", "000810", "010140", "064350", "138040",
                "051910", "010130", "009540", "267260", "066570",
                "066575", "033780", "003550", "003555", "310200"
        );

        return tickers.stream()
                .map(code -> {
                    try {
                        return getInvestorTrend(code, userId);
                    } catch (Exception e) {
                        log.warn("시장 투자자 동향 조회 실패: {}", code, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Long parseLong(Object value) {
        if (value == null) return 0L;
        String str = String.valueOf(value).replaceAll(",", "").trim();
        if (str.isEmpty()) return 0L;
        return Long.parseLong(str);
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            return new String(
                    Base64.getDecoder().decode(encoded),
                    StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "인증 정보 디코딩 실패"
            );
        }
    }
}
