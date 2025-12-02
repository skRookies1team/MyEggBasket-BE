package com.rookies4.finalProject.service;

import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisTransactionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
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

    private final RestTemplate restTemplate;

    /**
     * 한국투자증권 [주식일별주문체결조회] API 호출
     */
    public KisTransactionDto getDailyOrderHistory(User user, String accessToken, boolean useVirtual) {

        // 1. URL 설정 (KisApiConfig 기반)
        String path = "/uapi/domestic-stock/v1/trading/inquire-daily-ccld";
        URI uri = KisApiConfig.uri(useVirtual, path);

        // 2. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", decodeBase64(user.getAppkey()));
        headers.set("appsecret", decodeBase64(user.getAppsecret()));

        // TR_ID 설정 (실전: TTTC8001R, 모의: VTTC8001R)
        if (useVirtual) {
            headers.set("tr_id", "VTTC8001R");
        } else {
            headers.set("tr_id", "TTTC8001R");
        }

        // 3. 쿼리 파라미터 구성
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // YYYYMMDD

        String queryString = String.format(
                "?CANO=%s&ACNT_PRDT_CD=%s&INQR_STRT_DT=%s&INQR_END_DT=%s&SLL_BUY_DVSN_CD=00&INQR_DVSN=00&PDNO=&CTX_AREA_FK100=&CTX_AREA_NK100=",
                "50101234", // TODO: User.accountNumber 사용 필요
                "01",
                today,
                today
        );

        // 최종 URI 완성 (기존 URI + 쿼리스트림)
        URI fullUri = URI.create(uri.toString() + queryString);

        try {
            HttpEntity<?> entity = new HttpEntity<>(headers);
            return restTemplate.exchange(
                    fullUri,
                    HttpMethod.GET,
                    entity,
                    KisTransactionDto.class
            ).getBody();

        } catch (Exception e) {
            log.error("KIS API 주문내역 조회 실패: {}", e.getMessage());
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
            return encoded;
        }
    }
}