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

        // 분봉(minute) 요청일 경우 전용 메서드 호출
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

        // 일봉 등은 isMinute = false
        return buildResponse(stockCode, period, output2, false);
    }

    /**
     * [수정] 분봉 데이터 조회 (FHKST03010200)
     * 파라미터명 수정: FID_INPUT_HOUR -> FID_INPUT_HOUR_1
     */
    @SuppressWarnings("unchecked")
    private KisPeriodStockDTO.ChartResponse getMinuteChartData(String stockCode, Long userId) {
        // 현재 시간 (HHmmss)
        String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));

        KisApiRequest request = KisApiRequest.builder()
                .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                .trId("FHKST03010200") // 분봉용 TR ID
                .param("FID_ETC_CLS_CODE", "")
                .param("FID_COND_MRKT_DIV_CODE", "J")
                .param("FID_INPUT_ISCD", stockCode)
                .param("FID_INPUT_HOUR_1", currentTime) // [수정됨] API 문서 기준 올바른 파라미터명
                .param("FID_PW_DATA_INCU_YN", "Y")      // 과거 데이터 포함
                .useVirtualServer(false)
                .build();

        Map<String, Object> body = kisApiClient.get(userId, request, Map.class);
        validateApiResponse(body);

        List<Map<String, Object>> output2 = (List<Map<String, Object>>) body.get("output2");

        // 분봉은 isMinute = true
        return buildResponse(stockCode, "minute", output2, true);
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

        // KIS 분봉 데이터는 최신순(내림차순)으로 옴 -> 시간순(오름차순) 정렬 필요시 reverse
        Collections.reverse(chartData);

        log.info("[KIS] 차트 데이터 조회 성공 - StockCode: {}, Period: {}, DataCount: {}", stockCode, period, chartData.size());

        return KisPeriodStockDTO.ChartResponse.builder()
                .stockCode(stockCode)
                .period(period)
                .data(chartData)
                .build();
    }

    // 일봉 데이터 매핑
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

    // [수정됨] 분봉 데이터 매핑: 날짜 + 시간 포맷팅 적용
    private KisPeriodStockDTO.ChartData transformToMinuteChartData(Map<String, Object> output) {
        String date = (String) output.get("stck_bsop_date"); // YYYYMMDD
        String time = (String) output.get("stck_cntg_hour"); // HHmmss

        // 프론트엔드 호환성을 위해 "YYYY-MM-DD HH:mm:ss" 형식으로 변환
        String formattedTime = formatMinuteDateTime(date, time);

        return KisPeriodStockDTO.ChartData.builder()
                .time(formattedTime)
                .price(parseLong(output.get("stck_prpr")))
                .open(parseLong(output.get("stck_oprc")))
                .high(parseLong(output.get("stck_hgpr")))
                .low(parseLong(output.get("stck_lwpr")))
                .volume(parseLong(output.get("cntg_vol")))
                .build();
    }

    // [추가됨] 분봉 날짜/시간 포맷팅 헬퍼
    private String formatMinuteDateTime(String date, String time) {
        if (date == null || date.length() != 8 || time == null || time.length() != 6) {
            return time; // 데이터가 이상할 경우 원본 반환 (혹은 에러처리)
        }
        // "20240521" + "123000" -> "2024-05-21 12:30:00"
        return date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8) + " " +
                time.substring(0, 2) + ":" + time.substring(2, 4) + ":" + time.substring(4, 6);
    }

    private String getPeriodCode(String period) {
        switch (period.toLowerCase()) {
            case "day": return "D";
            case "week": return "W";
            case "month": return "M";
            case "year": return "Y";
            default: throw new BusinessException(ErrorCode.VALIDATION_ERROR, "유효하지 않은 기간입니다: " + period);
        }
    }

    private String getStartDate(String period) {
        LocalDate now = LocalDate.now();
        LocalDate startDate;
        switch (period.toLowerCase()) {
            case "day": startDate = now.minusMonths(6); break;
            case "week": startDate = now.minusYears(2); break;
            case "month":
            case "year": startDate = now.minusYears(5); break;
            default: throw new BusinessException(ErrorCode.VALIDATION_ERROR, "유효하지 않은 기간입니다: " + period);
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