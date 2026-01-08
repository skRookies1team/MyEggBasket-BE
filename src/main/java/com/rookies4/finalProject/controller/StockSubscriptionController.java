package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.StockSubscriptionDTO;
import com.rookies4.finalProject.service.StockSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 종목 구독 관리 컨트롤러
 */
@Tag(name = "Stock Subscription", description = "종목 구독 관리 API")
@RestController
@RequestMapping("/api/app/subscriptions")
@RequiredArgsConstructor
public class StockSubscriptionController {

    private final StockSubscriptionService stockSubscriptionService;

    @Operation(summary = "종목 구독", description = "사용자가 종목 조회를 시작합니다.")
    @PostMapping
    public ResponseEntity<StockSubscriptionDTO.SubscriptionResponse> subscribe(
            @Valid @RequestBody StockSubscriptionDTO.SubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stockSubscriptionService.subscribe(request));
    }

    @Operation(summary = "종목 구독 해지", description = "사용자가 종목 조회를 종료합니다.")
    @DeleteMapping("/{stockCode}")
    public ResponseEntity<Void> unsubscribe(@PathVariable String stockCode) {
        stockSubscriptionService.unsubscribe(stockCode);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 구독 종목 목록", description = "현재 구독 중인 종목 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<StockSubscriptionDTO.SubscriptionResponse>> getMySubscriptions() {
        return ResponseEntity.ok(stockSubscriptionService.getMySubscriptions());
    }

    @Operation(summary = "종목 구독자 수", description = "특정 종목을 구독 중인 사용자 수를 조회합니다.")
    @GetMapping("/{stockCode}/count")
    public ResponseEntity<Long> getSubscriberCount(@PathVariable String stockCode) {
        return ResponseEntity.ok(stockSubscriptionService.getSubscriberCount(stockCode));
    }

    /**
     * [추가] System/Collector용: 현재 활성화된 모든 구독 종목 코드 조회
     * Python Collector가 서버 시작 시 이 목록을 받아 KIS WS 구독을 초기화합니다.
     */
    @Operation(summary = "[System] 활성 구독 종목 전체 조회", description = "현재 구독 중인 모든 종목 코드를 중복 없이 조회합니다.")
    @GetMapping("/active-codes")
    public ResponseEntity<List<String>> getAllActiveStockCodes() {
        // Service에 getAllActiveStockCodes() 메소드가 필요합니다. (Repository의 Distinct Query 호출)
        return ResponseEntity.ok(stockSubscriptionService.getAllActiveStockCodes());
    }
}