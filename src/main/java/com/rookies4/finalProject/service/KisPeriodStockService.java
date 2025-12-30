package com.rookies4.finalProject.service;

import com.rookies4.finalProject.component.KisApiClient;
import com.rookies4.finalProject.dto.KisApiRequest;
import com.rookies4.finalProject.dto.KisPeriodStockDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KisPeriodStockService {

    private final KisApiClient kisApiClient;

    @SuppressWarnings("unchecked")
    public KisPeriodStockDTO.ChartResponse getChartData(String stockCode, String period, Long userId) {
        // [수정] minute 요청일 경우 별도 로직 처리
        if ("minute".equalsIgnoreCase(period)) {
            return getMinuteChartData(stockCode, userId);
        }

        String periodCode = getPeriodCode(period);
        String startDate = getStartDate(period);
        String endDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        KisApiRequest request = KisApiRequest.builder()
                .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                .trId("FHKST03010100")
                .param("FID_COND_MRKT_DIV_CODE", "J")
                .param("FID_INPUT_ISCD", stockCode)
                .param("FID_INPUT_DATE_1", startDate)
                .param("FID_INPUT_DATE_2", endDate)
                .param("FID_PERIOD_DIV_CODE", periodCode)
                .param("FID_ORG_ADJ_PRC", "1")
                .useVirtualServer(false)
                .build();

        Map<String, Object> body = kisApiClient.get(userId, request, Map.class);
        validateApiResponse(body);

        List<Map<String, Object>> output2 = (List<Map<String, Object>>) body.get("output2");

        return buildResponse(stockCode, period, output2, false); // isMinute = false
    }

    /**
     * [추가] 분봉(Time) 차트 데이터 조회 (FHKST03010200)
     */
    @SuppressWarnings("unchecked")
    private KisPeriodStockDTO.ChartResponse getMinuteChartData(String stockCode, Long userId) {
        // 현재 시간 (HHmmss) - 장 마감 후면 153000 등으로 고정해도 되지만, KIS API는 미래 시간을 넣으면 현재까지의 데이터를 줌
        String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));

        KisApiRequest request = KisApiRequest.builder()
                .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                .trId("FHKST03010200") // 분봉용 TR ID
                .param("FID_ETC_CLS_CODE", "")
                .param("FID_COND_MRKT_DIV_CODE", "J")
                .param("FID_INPUT_ISCD", stockCode)
                .param("FID_INPUT_HOUR", currentTime) // 현재 시간 기준 조회
                .param("FID_PW_DATA_INCU_YN", "Y")    // 과거 데이터 포함
                .useVirtualServer(false)
                .build();

        Map<String, Object> body = kisApiClient.get(userId, request, Map.class);
        validateApiResponse(body);

        List<Map<String, Object>> output2 = (List<Map<String, Object>>) body.get("output2");

        return buildResponse(stockCode, "minute", output2, true); // isMinute = true
    }

    private void validateApiResponse(Map<String, Object> body) {
        if (body == null || body.get("rt_cd") == null || ((String)body.get("rt_cd")).isEmpty()) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "KIS API로부터 유효하지 않은 응답을 받았습니다.");
        }
        if (!"0".equals(body.get("rt_cd"))) {
            String msg = (String) body.get("msg1");
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "차트 데이터 조회 실패: " + msg);
        }
    }

    private KisPeriodStockDTO.ChartResponse buildResponse(String stockCode, String period, List<Map<String, Object>> output2, boolean isMinute) {
        if (output2 == null) {
            return KisPeriodStockDTO.ChartResponse.builder()
                    .stockCode(stockCode)
                    .period(period)
                    .data(Collections.emptyList())
                    .build();
        }

        List<KisPeriodStockDTO.ChartData> chartData = output2.stream()
                .map(item -> isMinute ? transformToMinuteChartData(item) : transformToDailyChartData(item))
                .collect(Collectors.toList());

        // 분봉 데이터는 API가 최신순(내림차순)으로 줄 수 있으므로, 필요 시 시간순 정렬
        // (프론트엔드인 lightweight-charts는 오름차순을 요구함)
        Collections.reverse(chartData);

        log.info("[KIS] 차트 데이터 조회 성공 - StockCode: {}, Period: {}, DataCount: {}", stockCode, period, chartData.size());

        return KisPeriodStockDTO.ChartResponse.builder()
                .stockCode(stockCode)
                .period(period)
                .data(chartData)
                .build();
    }

    // [기존 로직] 일/주/월/년 데이터 변환
    private KisPeriodStockDTO.ChartData transformToDailyChartData(Map<String, Object> output) {
        return KisPeriodStockDTO.ChartData.builder()
                .time(formatApiDate((String) output.get("stck_bsop_date")))
                .price(parseLong(output.get("stck_clpr")))
                .open(parseLong(output.get("stck_oprc")))
                .high(parseLong(output.get("stck_hgpr")))
                .low(parseLong(output.get("stck_lwpr")))
                .volume(parseLong(output.get("acml_vol")))
                .build();
    }

    // [추가] 분봉 데이터 변환
    private KisPeriodStockDTO.ChartData transformToMinuteChartData(Map<String, Object> output) {
        // 분봉 API 필드명은 일봉과 다름
        // stck_cntg_hour: 시간
        // stck_prpr: 현재가(종가)
        // stck_oprc: 시가
        // stck_hgpr: 고가
        // stck_lwpr: 저가
        // cntg_vol: 체결거래량 (accumulated volume을 원하면 acml_vol 확인 필요, 보통 캔들은 해당 분의 거래량이므로 cntg_vol 사용)
        // 하지만 KIS API 'inquire-time-itemchartprice' output2에서는 'cntg_vol'이 그 시간대의 거래량임.

        return KisPeriodStockDTO.ChartData.builder()
                .time((String) output.get("stck_cntg_hour")) // HHmmss 포맷 그대로 반환 (프론트에서 처리)
                .price(parseLong(output.get("stck_prpr")))
                .open(parseLong(output.get("stck_oprc")))
                .high(parseLong(output.get("stck_hgpr")))
                .low(parseLong(output.get("stck_lwpr")))
                .volume(parseLong(output.get("cntg_vol")))
                .build();
    }

    private String getPeriodCode(String period) {
        switch (period.toLowerCase()) {
            case "day": return "D";
            case "week": return "W";
            case "month": return "M";
            case "year": return "Y";
            // minute는 별도 메서드에서 처리하므로 여기서는 에러
            default: throw new BusinessException(ErrorCode.VALIDATION_ERROR, "유효하지 않은 기간입니다: " + period);
        }
    }

    private String getStartDate(String period) {
        LocalDate now = LocalDate.now();
        LocalDate startDate;
        switch (period.toLowerCase()) {
            case "day":
                startDate = now.minusMonths(6);
                break;
            case "week":
                startDate = now.minusYears(2);
                break;
            case "month":
            case "year":
                startDate = now.minusYears(5);
                break;
            default:
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "유효하지 않은 기간입니다: " + period);
        }
        return startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private String formatApiDate(String dateStr) {
        if (dateStr == null || dateStr.length() != 8) return dateStr;
        return dateStr.substring(0, 4) + "-" + dateStr.substring(4, 6) + "-" + dateStr.substring(6, 8);
    }

    private Long parseLong(Object value) {
        if (value == null) return 0L;
        String strValue = String.valueOf(value).trim();
        if (strValue.isEmpty()) return 0L;
        return Long.parseLong(strValue.replaceAll(",", ""));
    }
}