package com.rookies4.finalProject.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.component.KisApiClient;
import com.rookies4.finalProject.component.SecureLogger;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisApiRequest;
import com.rookies4.finalProject.dto.KisTransactionDTO;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
        LocalDate nowKst = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String threeMonthday = nowKst.minusMonths(3).format(DateTimeFormatter.BASIC_ISO_DATE);
        String today = nowKst.format(DateTimeFormatter.BASIC_ISO_DATE);

        String cano = user.getAccount();
        String trId = useVirtual ? "VTTC0081R" : "TTTC0081R";

        // [수정] 타입 변경: Output1 -> KisOrderDetail
        List<KisTransactionDTO.KisOrderDetail> allHistory = new ArrayList<>();

        KisTransactionDTO lastResponseDto = new KisTransactionDTO();

        String ctxAreaFk = "";
        String ctxAreaNk = "";
        boolean hasNext = true;
        int pageCount = 0;

        log.info("[KIS_ORDER] 주문내역 전체 조회 시작: userId={}, period={}~{}", user.getId(), threeMonthday, today);

        while (hasNext) {
            pageCount++;
            if (pageCount > 100) {
                log.warn("[KIS_ORDER] 페이지네이션 횟수 초과로 중단합니다. userId={}", user.getId());
                break;
            }

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
                    .param("CTX_AREA_FK100", ctxAreaFk)
                    .param("CTX_AREA_NK100", ctxAreaNk)
                    .useVirtualServer(useVirtual)
                    .build();

            try {
                String bodyStr = kisApiClient.get(user.getId(), request, String.class);

                if (bodyStr == null || bodyStr.isBlank()) {
                    log.warn("[KIS_ORDER] 주문내역 조회 응답 body가 비어있음 (Page: {}), userId={}", pageCount, user.getId());
                    break;
                }

                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                KisTransactionDTO dto = objectMapper.readValue(bodyStr, KisTransactionDTO.class);

                lastResponseDto = dto;

                if (!"0".equals(dto.getRtCd())) {
                    log.error("[KIS_ORDER] 조회 실패: rt_cd={}, msg={}", dto.getRtCd(), dto.getMsg1());
                    break;
                }

                if (dto.getOutput1() != null) {
                    allHistory.addAll(dto.getOutput1());
                }

                log.info("[KIS_ORDER] {} 페이지 조회 완료. 건수: {}, 누적 건수: {}",
                        pageCount,
                        (dto.getOutput1() != null ? dto.getOutput1().size() : 0),
                        allHistory.size());

                // [수정] DTO에 추가한 필드 사용
                String nextNk = dto.getCtxAreaNk100();
                String nextFk = dto.getCtxAreaFk100();

                if (nextNk != null && !nextNk.trim().isEmpty()) {
                    ctxAreaNk = nextNk;
                    ctxAreaFk = nextFk;
                } else {
                    hasNext = false;
                }

            } catch (Exception e) {
                log.error("[KIS_ORDER] 주문내역 조회 또는 파싱 실패 (Page: {}): {}, userId={}", pageCount, e.getMessage(), user.getId(), e);
                hasNext = false;
            }
        }

        if (lastResponseDto != null) {
            lastResponseDto.setOutput1(allHistory);
            log.info("[KIS_ORDER] 전체 조회 완료. 총 {} 건 반환.", allHistory.size());
            return lastResponseDto;
        }

        return new KisTransactionDTO();
    }
}