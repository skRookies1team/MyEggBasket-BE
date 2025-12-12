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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

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
            ResponseEntity<Map> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    requestEntity,
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            
            // [디버깅] API 응답 로그
            try {
                log.info("KIS Investor Trend API Response: {}", objectMapper.writeValueAsString(body));
            } catch (Exception e) {
                log.error("Failed to log API response", e);
            }

            if (body == null) {
                throw new BusinessException(ErrorCode.KIS_API_ERROR, "투자자 동향 조회 응답이 비어있습니다.");
            }

            // rt_cd 확인 (성공이 아니면 에러)
            if (!"0".equals(body.get("rt_cd"))) {
                String msg = (String) body.get("msg1");
                throw new BusinessException(ErrorCode.KIS_API_ERROR, "투자자 동향 조회 실패: " + msg);
            }

            List<Map<String, Object>> outputList = (List<Map<String, Object>>) body.get("output");
            
            // 4. 데이터 가공
            // DB에서 종목명 조회
            String stockName = stockRepository.findById(stockCode)
                    .map(Stock::getName)
                    .orElse(null);

            List<KisInvestorTrendDTO.InvestorInfo> investors = new ArrayList<>();

            // output이 없거나 비어있으면 0으로 채워서 반환 (에러 발생시키지 않음)
            if (outputList == null || outputList.isEmpty()) {
                log.warn("투자자 동향 데이터가 없습니다 (output is empty). stockCode={}", stockCode);
                investors.add(new KisInvestorTrendDTO.InvestorInfo("개인", 0L, 0L));
                investors.add(new KisInvestorTrendDTO.InvestorInfo("외국인", 0L, 0L));
                investors.add(new KisInvestorTrendDTO.InvestorInfo("기관", 0L, 0L));
            } else {
                // 유효한 데이터 찾기 (0이 아닌 데이터)
                Map<String, Object> validOutput = outputList.get(0);
                
                for (Map<String, Object> output : outputList) {
                    long personalQty = parseLong(output.get("prsn_ntby_qty"));
                    long foreignerQty = parseLong(output.get("frgn_ntby_qty"));
                    long institutionQty = parseLong(output.get("orgn_ntby_qty"));

                    if (personalQty != 0 || foreignerQty != 0 || institutionQty != 0) {
                        validOutput = output;
                        break;
                    }
                }

                // API 응답에 종목명이 있고 DB 조회가 실패했다면 API 값 사용
                if (stockName == null && validOutput.containsKey("hts_kor_isnm")) {
                    stockName = (String) validOutput.get("hts_kor_isnm");
                }

                // 개인
                investors.add(new KisInvestorTrendDTO.InvestorInfo("개인",
                        parseLong(validOutput.get("prsn_ntby_qty")),
                        parseLong(validOutput.get("prsn_ntby_tr_pbmn")) * 1_000_000));

                // 외국인
                investors.add(new KisInvestorTrendDTO.InvestorInfo("외국인",
                        parseLong(validOutput.get("frgn_ntby_qty")),
                        parseLong(validOutput.get("frgn_ntby_tr_pbmn")) * 1_000_000));

                // 기관
                investors.add(new KisInvestorTrendDTO.InvestorInfo("기관",
                        parseLong(validOutput.get("orgn_ntby_qty")),
                        parseLong(validOutput.get("orgn_ntby_tr_pbmn")) * 1_000_000));
            }

            return KisInvestorTrendDTO.InvestorTrendResponse.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .investors(investors)
                    .build();

        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "KIS API 호출 실패: " + e.getMessage());
        }
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