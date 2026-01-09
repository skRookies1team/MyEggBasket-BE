package com.rookies4.finalProject.service;

import com.rookies4.finalProject.component.KisApiClient;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.dto.CurrentPriceDTO;
import com.rookies4.finalProject.dto.KisApiRequest;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisStockService {

    private final KisApiClient kisApiClient;
    private final StockRepository stockRepository;

    // 마지막 성공 가격을 저장할 메모리 캐시 (Key: 종목코드)
    private final Map<String, CachedPrice> priceCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5000; // 캐시 유효 시간 5초

    public CurrentPriceDTO getCurrentPrice(String stockCode, boolean useVirtualServer, Long userId) {
        validateStockCodeFormat(stockCode);

        // 1. 캐시 확인 (유효하면 반환)
        if (priceCache.containsKey(stockCode)) {
            CachedPrice cached = priceCache.get(stockCode);
            if (!cached.isExpired()) {
                return cached.getData();
            }
        }

        try {
            // KisApiClient를 사용한 API 호출
            KisApiRequest request = KisApiRequest.builder()
                    .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                    .trId("FHKST01010100")
                    .param("FID_COND_MRKT_DIV_CODE", "J")
                    .param("FID_INPUT_ISCD", stockCode)
                    .useVirtualServer(useVirtualServer)
                    .build();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = kisApiClient.get(userId, request, Map.class);

            CurrentPriceDTO result = mapToCurrentPrice(response, stockCode);

            // [성공 시] 캐시에 최신 데이터 저장
            priceCache.put(stockCode, new CachedPrice(result));

            return result;

        } catch (Exception e) {
            // [실패 시] 캐시 확인 및 폴백 처리
            if (priceCache.containsKey(stockCode)) {
                log.warn("[Fallback] '{}' API 조회 실패 -> 캐시된 과거 데이터 반환. (Error: {})", stockCode, e.getMessage());
                return priceCache.get(stockCode).getData();
            }

            // 캐시 데이터도 없으면 에러 로깅 후 throw
            log.error("[Error] '{}' API 조회 실패 & 캐시 데이터 없음. Error: {}", stockCode, e.getMessage());
            throw e;
        }
    }

    // 캐시용 내부 클래스
    private static class CachedPrice {
        private final CurrentPriceDTO data;
        private final long timestamp;

        public CachedPrice(CurrentPriceDTO data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS;
        }

        public CurrentPriceDTO getData() {
            return data;
        }
    }

    /**
     * KIS API 응답을 CurrentPriceDTO로 매핑
     */
    @SuppressWarnings("unchecked")
    private CurrentPriceDTO mapToCurrentPrice(Map<String, Object> body, String stockCode) {
        if (body == null || !"0".equals(body.get("rt_cd"))) {
            String msg = body != null ? (String) body.get("msg1") : "응답이 없습니다.";
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "현재가 조회 실패: " + msg);
        }

        Map<String, Object> output = (Map<String, Object>) body.get("output");
        if (output == null) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "현재가 조회 결과(output)가 없습니다.");
        }

        String stockName = stockRepository.findById(stockCode)
                .map(Stock::getName)
                .orElseGet(() -> (String) output.get("hts_kor_isnm"));

        BigDecimal currentPrice = parseBigDecimal(output.get("stck_prpr"));

        CurrentPriceDTO result = CurrentPriceDTO.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .currentPrice(currentPrice)
                .changeAmount(parseDouble(output.get("prdy_vrss")))
                .changeRate(parseDouble(output.get("prdy_ctrt")))
                .volume(parseLong(output.get("acml_vol")))
                .tradingValue(parseDouble(output.get("acml_tr_pbmn")))
                .openPrice(parseDouble(output.get("stck_oprc")))
                .highPrice(parseDouble(output.get("stck_hgpr")))
                .lowPrice(parseDouble(output.get("stck_lwpr")))
                .closePrice(currentPrice.doubleValue())
                .updatedAt(java.time.LocalDateTime.now())
                .build();

        log.info("[KIS] 현재가 조회 성공 - StockCode: {}, StockName: {}, Price: {}", stockCode, stockName, currentPrice);
        return result;
    }

    private void validateStockCodeFormat(String stockCode) {
        if (stockCode == null || stockCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "종목코드를 입력해주세요.");
        }
        if (!stockCode.matches("^\\d{6}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "올바른 종목코드 형식이 아닙니다. (6자리 숫자 필요)");
        }
    }

    private Double parseDouble(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty())
            return 0.0;
        return Double.parseDouble(String.valueOf(value).replace(",", ""));
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty())
            return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(value).replace(",", ""));
    }

    private Long parseLong(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty())
            return 0L;
        return Long.parseLong(String.valueOf(value).replace(",", ""));
    }
}