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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

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

    public KisInvestorTrendDTO.InvestorTrendResponse getInvestorTrend(String stockCode, Long userId) {
        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 토큰 발급 (실전투자 계좌로만 가능)
        KisAuthTokenDTO.KisTokenResponse tokenResponse = kisAuthService.issueToken(false, user);
        String accessToken = tokenResponse.getAccessToken();

        // 3. KIS API 호출
        String path = "/uapi/domestic-stock/v1/quotations/inquire-investor";
        URI uri = KisApiConfig.uri(false, path);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri)
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", stockCode);

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", decodeBase64(user.getAppkey()));
        headers.set("appsecret", decodeBase64(user.getAppsecret()));
        headers.set("tr_id", "FHKST01010900"); // 투자자별 매매동향
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<KisInvestorTrendDTO.KisApiResponse> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    requestEntity,
                    KisInvestorTrendDTO.KisApiResponse.class
            );

            KisInvestorTrendDTO.KisApiResponse body = response.getBody();
            if (body == null || !"0".equals(body.getRtCd()) || body.getOutput() == null || body.getOutput().isEmpty()) {
                String msg = body != null ? body.getMsg1() : "응답이 없습니다.";
                throw new BusinessException(ErrorCode.KIS_API_ERROR, "투자자 동향 조회 실패: " + msg);
            }

            // 4. 시간대별로 사용할 데이터 선택
            List<KisInvestorTrendDTO.KisOutput> outputList = body.getOutput();
            KisInvestorTrendDTO.KisOutput targetOutput;

            LocalTime now = LocalTime.now();
            LocalTime marketCloseTime = LocalTime.of(16, 0); // 오후 4시

            if (now.isBefore(marketCloseTime) && outputList.size() > 1) {
                targetOutput = outputList.get(1); // 전일 데이터
            } else {
                targetOutput = outputList.get(0); // 당일 데이터
            }

            // 5. 데이터 가공
            String stockName = stockRepository.findById(stockCode)
                    .map(Stock::getName)
                    .orElse(targetOutput.getStockName());

            List<KisInvestorTrendDTO.InvestorInfo> investors = new ArrayList<>();

            investors.add(new KisInvestorTrendDTO.InvestorInfo("개인",
                    parseLong(targetOutput.getPersonalNetBuyQty()),
                    parseLong(targetOutput.getPersonalNetBuyAmount()) * 1_000_000));

            investors.add(new KisInvestorTrendDTO.InvestorInfo("외국인",
                    parseLong(targetOutput.getForeignerNetBuyQty()),
                    parseLong(targetOutput.getForeignerNetBuyAmount()) * 1_000_000));

            investors.add(new KisInvestorTrendDTO.InvestorInfo("기관",
                    parseLong(targetOutput.getInstitutionNetBuyQty()),
                    parseLong(targetOutput.getInstitutionNetBuyAmount()) * 1_000_000));

            return KisInvestorTrendDTO.InvestorTrendResponse.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .closePrice(parseLong(targetOutput.getClosePrice()))
                    .changeAmount(parseLong(targetOutput.getChangeAmount()))
                    .changeSign(targetOutput.getChangeSign())
                    .investors(investors)
                    .build();

        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "KIS API 호출 실패: " + e.getMessage());
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
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Base64 디코딩 실패: {}", encoded, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "잘못된 형식의 인증 정보입니다.");
        }
    }
}