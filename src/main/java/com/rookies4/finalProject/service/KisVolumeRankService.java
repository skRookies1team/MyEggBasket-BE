package com.rookies4.finalProject.service;

import com.rookies4.finalProject.component.KisApiClient;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisApiRequest;
import com.rookies4.finalProject.dto.KisVolumeRankDTO;
import com.rookies4.finalProject.dto.VolumeRankResponseDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KisVolumeRankService {

    private static final Logger log = LoggerFactory.getLogger(KisVolumeRankService.class);
    private final KisApiClient kisApiClient;

    // [캐시 추가] 메모리에 결과 저장 (키: "RANK", 값: 데이터+만료시간)
    private final Map<String, CachedRank> cache = new ConcurrentHashMap<>();

    private record CachedRank(List<VolumeRankResponseDTO> data, LocalDateTime expireAt) {
    }

    public List<VolumeRankResponseDTO> getVolumeRank(User user) {
        // 1. 캐시 확인 (유효기간 1분)
        if (cache.containsKey("RANK")) {
            CachedRank cached = cache.get("RANK");
            if (LocalDateTime.now().isBefore(cached.expireAt())) {
                return cached.data(); // API 호출 없이 바로 반환
            }
        }

        log.info("[KIS] 거래량 순위 조회 시작 (API 호출) - UserId: {}", user.getId());

        KisApiRequest request = KisApiRequest.builder()
                .path("/uapi/domestic-stock/v1/quotations/volume-rank")
                .trId("FHPST01710000")
                .param("FID_COND_MRKT_DIV_CODE", "J")
                .param("FID_COND_SCR_DIV_CODE", "20171")
                .param("FID_INPUT_ISCD", "0000")
                .param("FID_DIV_CLS_CODE", "0")
                .param("FID_BLNG_CLS_CODE", "0")
                .param("FID_TRGT_CLS_CODE", "111111111")
                .param("FID_TRGT_EXLS_CLS_CODE", "000000")
                .param("FID_INPUT_PRICE_1", "")
                .param("FID_INPUT_PRICE_2", "")
                .param("FID_VOL_CNT", "")
                .param("FID_INPUT_DATE_1", "")
                .useVirtualServer(false)
                .build();

        KisVolumeRankDTO.KisVolumeRankResponse body =
                kisApiClient.get(user.getId(), request, KisVolumeRankDTO.KisVolumeRankResponse.class);

        if (body == null || body.getOutput() == null) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "거래량 순위 조회 응답이 비어있습니다.");
        }

        if (!"0".equals(body.getRtCd())) {
            log.error("KIS 거래량 순위 조회 실패: rt_cd={}, msg={}", body.getRtCd(), body.getMsg1());
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "거래량 순위 조회 실패: " + body.getMsg1());
        }

        List<VolumeRankResponseDTO> result = body.getOutput().stream()
                .limit(10)
                .map(this::transformToResponseDTO)
                .collect(Collectors.toList());

        // 2. 결과 캐싱 (1분간 유효)
        cache.put("RANK", new CachedRank(result, LocalDateTime.now().plusMinutes(1)));

        log.info("[KIS] 거래량 순위 조회 성공 및 캐싱 완료");

        return result;
    }

    private VolumeRankResponseDTO transformToResponseDTO(KisVolumeRankDTO.VolumeRankItem item) {
        return VolumeRankResponseDTO.builder()
                .rank(Integer.parseInt(item.getRank()))
                .code(item.getStockCode())
                .name(item.getStockName())
                .price(new BigDecimal(item.getCurrentPrice().replace(",", "")))
                .change(new BigDecimal(item.getPriceChange().replace(",", "")))
                .rate(Double.parseDouble(item.getChangeRate()))
                .volume(Long.parseLong(item.getVolume().replace(",", "")))
                .prevVolume(Long.parseLong(item.getPrevVolume().replace(",", "")))
                .turnover(Double.parseDouble(item.getVolInrt()))
                .build();
    }
}