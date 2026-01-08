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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisBalanceService {

    private static final String ACCOUNT_PRODUCT_CODE = "01";

    private final KisApiClient kisApiClient;
    private final ObjectMapper objectMapper;

    // [캐시 추가] 유저 ID별 잔고 캐싱 (Key: userId, Value: 잔고데이터 + 만료시간)
    private final Map<Long, CachedBalance> cache = new ConcurrentHashMap<>();

    // 캐시 데이터 구조 (데이터와 만료시간을 함께 저장)
    private record CachedBalance(KisBalanceDTO data, LocalDateTime expireAt) {}

    public KisBalanceDTO getBalanceFromKis(User user, String accessToken, boolean useVirtual) {
        // 1. 캐시 확인 (새로고침 시 API 호출 방지)
        if (cache.containsKey(user.getId())) {
            CachedBalance cached = cache.get(user.getId());
            // 현재 시간이 만료 시간 이전이면 캐시된 데이터 반환
            if (LocalDateTime.now().isBefore(cached.expireAt())) {
                log.info("[KIS] 잔고 조회 - 캐시된 데이터 반환 (UserId: {})", user.getId());
                return cached.data();
            }
            // 만료되었으면 삭제
            cache.remove(user.getId());
        }

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
            log.info("[KIS] 잔고 조회 API 실제 호출 (UserId: {})", user.getId());
            String bodyStr = kisApiClient.get(user.getId(), request, String.class);

            if (bodyStr == null || bodyStr.isBlank()) {
                log.warn("[KIS] 잔고 조회 응답 body가 비어있음, userId={}", user.getId());
                return new KisBalanceDTO();
            }

            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            KisBalanceDTO dto = objectMapper.readValue(bodyStr, KisBalanceDTO.class);

            log.info("[KIS] 잔고 조회 성공 및 파싱 완료 - Msg: {}", dto.getMsg1());

            // 2. 결과 캐싱 (30초간 유효)
            // 30초 내에 다시 조회하면 API를 호출하지 않음
            cache.put(user.getId(), new CachedBalance(dto, LocalDateTime.now().plusSeconds(30)));

            return dto;

        } catch (Exception e) {
            log.error("[KIS] 잔고 조회 또는 응답 파싱 실패: {}, userId = {}", e.getMessage(), user.getId(), e);
            return new KisBalanceDTO();
        }
    }

    /**
     * [옵션] 매수/매도 주문 성공 시 호출하여 잔고 캐시를 즉시 초기화하는 메소드
     * (TradeController 등에서 주문 완료 후 호출해주면 더 정확합니다)
     */
    public void clearCache(Long userId) {
        cache.remove(userId);
        log.info("[KIS] 잔고 캐시 초기화 완료 (UserId: {})", userId);
    }
}