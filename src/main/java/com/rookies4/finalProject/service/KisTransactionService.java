package com.rookies4.finalProject.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.component.KisApiClient;
import com.rookies4.finalProject.component.SecureLogger;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisApiRequest;
import com.rookies4.finalProject.dto.KisTransactionDTO;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisTransactionService {

    private static final String ACCOUNT_PRODUCT_CODE = "01";

    private final KisApiClient kisApiClient;
    private final ObjectMapper objectMapper;
    private final SecureLogger secureLogger;

    public KisTransactionDTO getDailyOrderHistory(User user, String accessToken, boolean useVirtual) {
        String threeMonthday = LocalDate.now().minusMonths(3).format(DateTimeFormatter.BASIC_ISO_DATE);
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String cano = user.getAccount();
        String trId = useVirtual ? "VTTC0081R" : "TTTC0081R";

        KisApiRequest request = KisApiRequest.builder()
                .path("/uapi/domestic-stock/v1/trading/inquire-daily-ccld")
                .trId(trId)
                .param("CANO", cano)
                .param("ACNT_PRDT_CD", ACCOUNT_PRODUCT_CODE)
                .param("INQR_STRT_DT", threeMonthday)
                .param("INQR_END_DT", today)
                .param("SLL_BUY_DVSN_CD", "00")
                .param("PDNO", "")
                .param("ORD_GNO_BRNO", "00000")
                .param("ODNO", "")
                .param("CCLD_DVSN", "00")
                .param("INQR_DVSN", "00")
                .param("INQR_DVSN_1", "")
                .param("INQR_DVSN_3", "00")
                .param("EXCG_ID_DVSN_CD", "KRX")
                .param("CTX_AREA_FK100", "")
                .param("CTX_AREA_NK100", "")
                .useVirtualServer(useVirtual)
                .build();

        log.info("[KIS_ORDER] 주문내역 조회 요청: userId={}", user.getId());

        try {
            String bodyStr = kisApiClient.get(user.getId(), request, String.class);
            
            log.info("[KIS_ORDER] raw 응답: body={}", secureLogger.maskSensitive(bodyStr));

            if (bodyStr == null || bodyStr.isBlank()) {
                log.warn("[KIS_ORDER] 주문내역 조회 응답 body 가 비어있음, userId={}", user.getId());
                return new KisTransactionDTO();
            }

            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            KisTransactionDTO dto = objectMapper.readValue(bodyStr, KisTransactionDTO.class);

            log.info("[KIS_ORDER] 파싱 결과: rt_cd={}, msg_cd={}, msg1={}, output1.size={}",
                    dto.getRtCd(),
                    dto.getMsgCd(),
                    dto.getMsg1(),
                    dto.getOutput1() == null ? null : dto.getOutput1().size());

            return dto;

        } catch (Exception e) {
            log.error("[KIS_ORDER] 주문내역 조회 또는 파싱 실패: {}, userId={}", e.getMessage(), user.getId(), e);
            return new KisTransactionDTO();
        }
    }
}