package com.rookies4.finalProject.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisTransactionDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisOrderHistoryService {

    private static final String ACCOUNT_PRODUCT_CODE = "01"; // 일반 위탁계좌 상품 코드 (계좌번호 뒷 2자리)

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper; // 스프링 기본 ObjectMapper 주입

    /**
     * 한국투자증권 [주식일별주문체결조회] API 호출
     */
    public KisTransactionDto getDailyOrderHistory(User user, String accessToken, boolean useVirtual) {

        // 1. URL 설정
        String path = "/uapi/domestic-stock/v1/trading/inquire-daily-ccld";
        URI baseUri = KisApiConfig.uri(useVirtual, path);

        // 2. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", decodeBase64(user.getAppkey()));
        headers.set("appsecret", decodeBase64(user.getAppsecret()));

        // TR_ID 설정 (실전: TTTC8001R, 모의: VTTC8001R)
        if (useVirtual) {
            headers.set("tr_id", "VTTC0081R");
        } else {
            headers.set("tr_id", "TTTC0081R");
        }

        // 고객 타입: 개인(P) / 법인(B) — Required=Y
        headers.set("custtype", "P"); // 개인 기준

        // 3. 쿼리 파라미터 구성
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // YYYYMMDD
        String cano = user.getAccount();

        var builder = org.springframework.web.util.UriComponentsBuilder
                .fromUri(baseUri)
                .queryParam("CANO", cano)                               // 종합계좌번호 (앞 8자리)
                .queryParam("ACNT_PRDT_CD", ACCOUNT_PRODUCT_CODE)       // 계좌상품코드 (뒤 2자리)
                .queryParam("INQR_STRT_DT", today)                      // 조회시작일자
                .queryParam("INQR_END_DT", today)                       // 조회종료일자
                .queryParam("SLL_BUY_DVSN_CD", "00")            // 00: 전체
                .queryParam("PDNO", "")                         // 종목번호 (옵션)
                .queryParam("ORD_GNO_BRNO", "00000")            // 주문채번지점번호 (Required=Y, 전체 조회용 기본값)
                .queryParam("ODNO", "")                         // 주문번호 (옵션)
                .queryParam("CCLD_DVSN", "00")                  // 체결구분 00: 전체
                .queryParam("INQR_DVSN", "00")                  // 조회구분 00: 역순
                .queryParam("INQR_DVSN_1", "")                  // 조회구분1 (없음: 전체) → 빈값
                .queryParam("INQR_DVSN_3", "00")                // 조회구분3 00: 전체
                .queryParam("EXCG_ID_DVSN_CD", "KRX")           // 거래소ID구분코드 (모의는 KRX만)
                .queryParam("CTX_AREA_FK100", "")               // 연속조회조건 (첫 조회는 공란)
                .queryParam("CTX_AREA_NK100", "");              // 연속조회키 (첫 조회는 공란)

        URI fullUri = builder.build(true).toUri();

        log.info("[KIS_ORDER] 주문내역 조회 요청: uri={}, userId={}", fullUri, user.getId());

        try {
            HttpEntity<?> entity = new HttpEntity<>(headers);

            // 1) raw String으로 먼저 응답 받기
            ResponseEntity<String> resp = restTemplate.exchange(
                    fullUri,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            String bodyStr = resp.getBody();
            log.info("[KIS_ORDER] raw 응답: status={}, body={}",
                    resp.getStatusCode(), bodyStr);

            if (bodyStr == null || bodyStr.isBlank()) {
                log.warn("[KIS_ORDER] 주문내역 조회 응답 body 가 비어있음, userId={}", user.getId());
                return new KisTransactionDto();
            }

            // 2) DTO로 파싱 (output1 매핑)
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            KisTransactionDto dto = objectMapper.readValue(bodyStr, KisTransactionDto.class);

            log.info("[KIS_ORDER] 파싱 결과: rt_cd={}, msg_cd={}, msg1={}, output1.size={}",
                    dto.getRtCd(),
                    dto.getMsgCd(),
                    dto.getMsg1(),
                    dto.getOutput1() == null ? null : dto.getOutput1().size());

            return dto;

        } catch (RestClientResponseException e) {
            log.error("[KIS_ORDER] 주문내역 조회 실패 (HTTP {}): {}, userId={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), user.getId(), e);
            return new KisTransactionDto();
        } catch (RestClientException e) {
            log.error("[KIS_ORDER] 주문내역 조회 중 오류: {}, userId={}", e.getMessage(), user.getId(), e);
            return new KisTransactionDto();
        } catch (Exception e) {
            log.error("[KIS_ORDER] 응답 파싱 실패: {}, userId={}", e.getMessage(), user.getId(), e);
            return new KisTransactionDto();
        }
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Base64 디코딩 실패, 원본 값 사용: {}", e.getMessage());
            return encoded;
        }
    }
}