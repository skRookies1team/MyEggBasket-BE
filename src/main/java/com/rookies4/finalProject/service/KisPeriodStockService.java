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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisPeriodStockService {

    private final KisApiClient kisApiClient;

    @SuppressWarnings("unchecked")
    public KisPeriodStockDTO.ChartResponse getChartData(String stockCode, String period, Long userId) {

        // 분봉(minute) 요청일 경우 전용 메서드 호출
        if ("minute".equalsIgnoreCase(period)) {
            return getMinuteChartData(stockCode, userId);
        }

        String periodCode = getPeriodCode(period);
        // 목표 시작 날짜 (예: 5년 전)
        String targetStartDateStr = getStartDate(period);
        // 조회 끝 날짜 (초기값: 오늘)
        String currentEndDateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        List<Map<String, Object>> allData = new ArrayList<>();

        // 반복 조회 로직 (Pagination)
        // KIS API는 한 번에 100건만 주므로, 과거 데이터가 더 있으면 end date를 옮겨서 다시 요청
        int maxCalls = 10; // 무한 루프 방지용 (최대 20번 호출 -> 약 2000일, 4~5년치 데이터)
        int callCount = 0;

        while (callCount < maxCalls) {
            try {
                // API 연속 호출 시 차단 방지를 위한 짧은 대기 (선택 사항)
                if (callCount > 0) Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            KisApiRequest request = KisApiRequest.builder()
                    .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                    .trId("FHKST03010100")
                    .param("FID_COND_MRKT_DIV_CODE", "J")
                    .param("FID_INPUT_ISCD", stockCode)
                    .param("FID_INPUT_DATE_1", targetStartDateStr) // 목표 시작일은 고정
                    .param("FID_INPUT_DATE_2", currentEndDateStr)  // 검색 끝나는 날짜는 계속 과거로 이동
                    .param("FID_PERIOD_DIV_CODE", periodCode)
                    .param("FID_ORG_ADJ_PRC", "1")
                    .useVirtualServer(false)
                    .build();

            Map<String, Object> body = kisApiClient.get(userId, request, Map.class);
            validateApiResponse(body);

            List<Map<String, Object>> batch = (List<Map<String, Object>>) body.get("output2");

            if (batch == null || batch.isEmpty()) {
                break; // 더 이상 데이터가 없으면 종료
            }

            // 조회된 데이터를 전체 리스트에 추가
            allData.addAll(batch);

            // 받은 데이터가 100건 미만이면 더 이상 과거 데이터가 없다는 뜻 -> 종료
            if (batch.size() < 100) {
                break;
            }

            // 이번 배치에서 가장 오래된 날짜 확인 (KIS 데이터는 내림차순이므로 마지막 인덱스)
            String oldestDateInBatch = (String) batch.get(batch.size() - 1).get("stck_bsop_date");

            // 목표 시작일보다 더 과거이거나 같으면 충분히 조회한 것 -> 종료
            if (oldestDateInBatch.compareTo(targetStartDateStr) <= 0) {
                break;
            }

            // [다음 반복 준비]
            // 가장 오래된 날짜의 '하루 전'을 새로운 종료일(FID_INPUT_DATE_2)로 설정
            LocalDate oldestDate = LocalDate.parse(oldestDateInBatch, DateTimeFormatter.ofPattern("yyyyMMdd"));
            currentEndDateStr = oldestDate.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            callCount++;
        }

        // 수집된 모든 데이터를 변환하여 반환
        return buildResponse(stockCode, period, allData, false);
    }

    /**
     * 분봉 데이터 조회 (FHKST03010200)
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
                .param("FID_INPUT_HOUR_1", currentTime)
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

        // KIS 데이터는 최신순(내림차순)으로 옴. 반복 조회로 쌓인 데이터도 [최신..과거], [더과거..더더과거] 순임.
        // 차트 라이브러리는 보통 시간 오름차순(과거->미래)을 원하므로 전체를 뒤집어줌.
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

    // 분봉 데이터 매핑
    private KisPeriodStockDTO.ChartData transformToMinuteChartData(Map<String, Object> output) {
        String date = (String) output.get("stck_bsop_date"); // YYYYMMDD
        String time = (String) output.get("stck_cntg_hour"); // HHmmss

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

    private String formatMinuteDateTime(String date, String time) {
        if (date == null || date.length() != 8 || time == null || time.length() != 6) {
            return time;
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
            case "day":
                startDate = now.minusYears(5); // 일봉 5년
                break;
            case "week":
                startDate = now.minusYears(10); // 주봉 10년
                break;
            case "month":
            case "year":
                startDate = now.minusYears(20); // 월/년 20년
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