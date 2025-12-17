package com.rookies4.finalProject.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.component.KisApiClient;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisApiRequest;
import com.rookies4.finalProject.dto.KisBalanceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisBalanceService {

    private static final String ACCOUNT_PRODUCT_CODE = "01";

    private final KisApiClient kisApiClient;
    private final ObjectMapper objectMapper;

    public KisBalanceDTO getBalanceFromKis(User user, String accessToken, boolean useVirtual) {
        String cano = user.getAccount();
        String trId = useVirtual ? "VTTC8434R" : "TTTC8434R";

        KisApiRequest request = KisApiRequest.builder()
                .path("/uapi/domestic-stock/v1/trading/inquire-balance")
                .trId(trId)
                .param("CANO", cano)
                .param("ACNT_PRDT_CD", ACCOUNT_PRODUCT_CODE)
                .param("AFHR_FLPR_YN", "N")
                .param("OFL_YN", "")
                .param("INQR_DVSN", "02")
                .param("UNPR_DVSN", "01")
                .param("FUND_STTL_ICLD_YN", "N")
                .param("FNCG_AMT_AUTO_RDPT_YN", "N")
                .param("PRCS_DVSN", "00")
                .param("CTX_AREA_FK100", "")
                .param("CTX_AREA_NK100", "")
                .useVirtualServer(useVirtual)
                .build();

        try {
            String bodyStr = kisApiClient.get(user.getId(), request, String.class);

            if (bodyStr == null || bodyStr.isBlank()) {
                log.warn("[KIS] 잔고 조회 응답 body가 비어있음, userId={}", user.getId());
                return new KisBalanceDTO();
            }

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

        } catch (Exception e) {
            log.error("[KIS] 잔고 조회 또는 응답 파싱 실패: {}, userId = {}", e.getMessage(), user.getId(), e);
            return new KisBalanceDTO();
        }
    }
}