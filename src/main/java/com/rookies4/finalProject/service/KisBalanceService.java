package com.rookies4.finalProject.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisBalanceDTO;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class KisBalanceService {

    private static final String ACCOUNT_PRODUCT_CODE = "01"; // 일반 위탁계좌 상품 코드 (계좌번호 뒷 2자리)

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper; // 스프링 기본 ObjectMapper 주입

    // KIS [주식잔고조회] API 호출
    public KisBalanceDTO getBalanceFromKis(User user, String accessToken, boolean useVirtual) {

        // 1. URL 설정
        String path = "/uapi/domestic-stock/v1/trading/inquire-balance";
        URI baseUri = KisApiConfig.uri(useVirtual, path);

        // 2. 요청 헤더 설정
        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON); // 요청 형식 JSON
        headers.setAccept(List.of(MediaType.APPLICATION_JSON)); // 응답 형식 JSON

        headers.set("authorization", "Bearer " + accessToken); // KIS가 요구하는 accessToken
        headers.set("appkey", decodeBase64(user.getAppkey())); // 암호화된 appkey 디코딩해서 삽입
        headers.set("appsecret", decodeBase64(user.getAppsecret())); // 암호화된 appsecret 디코딩해서 삽입

        // TR_ID 설정 (실전: TTTC8001R, 모의: VTTC8001R)
        if (useVirtual) {
            headers.set("tr_id", "VTTC0081R");
        } else {
            headers.set("tr_id", "TTTC0081R");
        }

        // 고객 타입: 개인(P) / 법인(B) — Required=Y
        headers.set("custtype", "P"); // 개인 기준

        // 3. 요청 쿼리 파라미터 구성
        String cano = user.getAccount();

        var builder = org.springframework.web.util.UriComponentsBuilder
                .fromUri(baseUri)
                .queryParam("CANO", cano)                        // 종합계좌번호 (앞 8자리)
                .queryParam("ACNT_PRDT_CD", ACCOUNT_PRODUCT_CODE) // 계좌상품코드 (뒤 2자리)
                .queryParam("AFHR_FLPR_YN", "N")                 // 시간외단일가, 거래소여부 (N: 기본값)
                .queryParam("OFL_YN", "")                        // 오프라인 여부 (공란: 기본값)
                .queryParam("INQR_DVSN", "01")                   // 조회구분 (01: 대출일별, 02: 종목별) - 필요시 변경
                .queryParam("UNPR_DVSN", "01")                   // 단가구분 (01: 기본값)
                .queryParam("FUND_STTL_ICLD_YN", "N")            // 펀드결제분 포함여부 (N: 포함하지 않음)
                .queryParam("FNCG_AMT_AUTO_RDPT_YN", "N")        // 융자금액 자동상환 여부 (N: 기본값)
                .queryParam("PRCS_DVSN", "00")                   // 처리구분 (00: 전일매매포함, 01: 전일매매미포함)
                .queryParam("CTX_AREA_FK100", "")                // 연속조회검색조건100 (첫 조회: 공란)
                .queryParam("CTX_AREA_NK100", "");               // 연속조회키100 (첫 조회: 공란)

        // 4. 최종 URI 설정
        URI fullUri = builder.build(true).toUri();

        // log.info("[KIS_ORDER] 주문내역 조회 요청: uri={}, userId={}", fullUri, user.getId());

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

            if (bodyStr == null || bodyStr.isBlank()) {
                log.warn("[KIS] 잔고 조회 응답 body가 비어있음, userId={}", user.getId());
                return new KisBalanceDTO();
            }

            // 2) DTO로 파싱 (output1 매핑)
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            KisBalanceDTO dto = objectMapper.readValue(bodyStr, KisBalanceDTO.class);

            log.info("[KIS] 파싱 결과: 성공/실패 여부 = {}, 응답 코드 = {}, 응답 메시지 = {}, 연속 조회 검색 조건 = {}, 연속 조회키 = {}, output1 크기 = {}",
                    dto.getRtCd(),
                    dto.getMsgCd(),
                    dto.getMsg1(),
                    dto.getCtxAreaFk100(),
                    dto.getCtxAreaNk100(),
                    dto.getOutput1() == null ? null : dto.getOutput1().size());

            return dto;

        } catch (RestClientResponseException e) {
            log.error("[KIS] 잔고 조회 실패 (HTTP {}): {}, userId = {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), user.getId(), e);
            return new KisBalanceDTO();
        } catch (RestClientException e) {
            log.error("[KIS] 잔고 조회 중 오류: {}, userId = {}", e.getMessage(), user.getId(), e);
            return new KisBalanceDTO();
        } catch (Exception e) {
            log.error("[KIS] 응답 파싱 실패: {}, userId = {}", e.getMessage(), user.getId(), e);
            return new KisBalanceDTO();
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