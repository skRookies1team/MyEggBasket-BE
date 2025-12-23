package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.CurrentPriceDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.KisStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/kis/stock")
@RequiredArgsConstructor
public class KisStockController {

    private final KisStockService kisStockService;

    /**
     * 종목 현재가 조회
     * @param stockCode 종목코드 (6자리)
     * @param useVirtualServer 모의투자 여부
     */
    @GetMapping("/current-price/{stockCode}")
    public ResponseEntity<CurrentPriceDTO> getCurrentPrice(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "false") boolean useVirtualServer) {

        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        CurrentPriceDTO response = kisStockService.getCurrentPrice(stockCode, useVirtualServer, userId);
        return ResponseEntity.ok(response);
    }
}