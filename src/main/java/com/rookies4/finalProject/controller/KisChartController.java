package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.KisChartDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.KisChartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/kis/chart")
@RequiredArgsConstructor
@Tag(name = "KIS 기간별 시세", description = "한국투자증권 일/주/월/년 시세 조회 API")
public class KisChartController {

    private final KisChartService kisChartService;

    @GetMapping("/{stockCode}")
    @Operation(summary = "기간별 시세 조회", description = "특정 종목의 일/주/월/년 단위 시세 데이터를 조회합니다. (로그인 필요)")
    public ResponseEntity<KisChartDTO.ChartResponse> getChartData(
            @Parameter(description = "종목코드 (예: 005930)") @PathVariable String stockCode,
            @Parameter(description = "조회 기간 (day, week, month, year)") @RequestParam String period) {

        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        KisChartDTO.ChartResponse response =
                kisChartService.getChartData(stockCode, period, userId);

        return ResponseEntity.ok(response);
    }
}