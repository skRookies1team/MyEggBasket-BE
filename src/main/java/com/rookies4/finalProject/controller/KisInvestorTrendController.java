package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.KisInvestorTrendDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.KisInvestorTrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/kis/investor-trend")
@RequiredArgsConstructor
@Tag(name = "KIS 투자자 동향", description = "한국투자증권 투자자 동향 조회 API")
public class KisInvestorTrendController {

    private final KisInvestorTrendService kisInvestorTrendService;

    @GetMapping("/{stockCode}")
    @Operation(summary = "종목별 투자자 동향 조회", description = "특정 종목의 개인, 외국인, 기관의 순매수/순매도 동향을 조회합니다. (로그인 필요)")
    public ResponseEntity<KisInvestorTrendDTO.InvestorTrendResponse> getInvestorTrend(
            @PathVariable String stockCode) {

        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        KisInvestorTrendDTO.InvestorTrendResponse response =
                kisInvestorTrendService.getInvestorTrendSingle(stockCode, userId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/market")
    @Operation(summary = "주요 종목 투자자 동향 일괄 조회", description = "지정된 주요 종목들의 투자자 동향을 일괄 조회합니다. (로그인 필요)")
    public ResponseEntity<List<KisInvestorTrendDTO.InvestorTrendResponse>>
    getMarketInvestorTrend() {

        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        return ResponseEntity.ok(
                kisInvestorTrendService.getMarketInvestorTrend(userId)
        );
    }
}