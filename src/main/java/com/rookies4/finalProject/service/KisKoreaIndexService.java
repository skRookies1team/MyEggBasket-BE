package com.rookies4.finalProject.service;

import com.rookies4.finalProject.component.KisApiClient;
import com.rookies4.finalProject.dto.KisApiRequest;
import com.rookies4.finalProject.dto.KisKoreaIndexDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KisKoreaIndexService {

    private final KisApiClient kisApiClient;

    // [캐시 추가] 지수 코드별 캐싱 (Key: IndexCode, Value: Response+Expire)
    private final Map<String, CachedIndex> cache = new ConcurrentHashMap<>();

    private record CachedIndex(KisKoreaIndexDTO.KisKoreaIndexResponse data, LocalDateTime expireAt) {}

    public KisKoreaIndexDTO.KisKoreaIndexResponse getKoreaIndex(String indexCode, Long userId) {
        // 1. 캐시 확인 (1분 유효)
        if (cache.containsKey(indexCode)) {
            CachedIndex cached = cache.get(indexCode);
            if (LocalDateTime.now().isBefore(cached.expireAt())) {
                return cached.data();
            }
        }

        // 캐시 없거나 만료됨 -> API 호출
        KisApiRequest request = KisApiRequest.builder()
                .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                .trId("FHPUP02100000")
                .param("FID_COND_MRKT_DIV_CODE", "U")
                .param("FID_INPUT_ISCD", indexCode)
                .useVirtualServer(false)
                .build();

        KisKoreaIndexDTO.KisKoreaIndexResponse response =
                kisApiClient.get(userId, request, KisKoreaIndexDTO.KisKoreaIndexResponse.class);

        if (response == null || !"0".equals(response.getRtCd())) {
            String msg = response != null ? response.getMsg1() : "응답이 없습니다.";
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "국내 지수 조회 실패: " + msg);
        }

        // 2. 결과 캐싱 (1분)
        cache.put(indexCode, new CachedIndex(response, LocalDateTime.now().plusMinutes(1)));
        log.info("[KIS] 국내 지수 조회 성공 및 캐싱 - IndexCode: {}", indexCode);

        return response;
    }
}