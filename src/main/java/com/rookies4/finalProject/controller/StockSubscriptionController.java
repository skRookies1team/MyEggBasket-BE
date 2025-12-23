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
 * 
 * 사용자가 현재 조회 중인(구독 중인) 종목을 관리합니다.
 * - 기본 종목 외에 사용자가 추가로 조회 중인 종목
 * - 구독/해지 시 Kafka 이벤트 발행
 */
@Tag(name = "Stock Subscription", description = "종목 구독 관리 API")
@RestController
@RequestMapping("/api/app/subscriptions")
@RequiredArgsConstructor
public class StockSubscriptionController {
    
    private final StockSubscriptionService stockSubscriptionService;

    /**
     * 종목 구독 (조회 시작)
     * 사용자가 특정 종목을 조회하기 시작할 때 호출합니다.
     * StockCollector가 해당 종목의 실시간 데이터를 수집하기 시작합니다.
     */
    @Operation(summary = "종목 구독", description = "사용자가 종목 조회를 시작합니다.")
    @PostMapping
    public ResponseEntity<StockSubscriptionDTO.SubscriptionResponse> subscribe(
            @Valid @RequestBody StockSubscriptionDTO.SubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stockSubscriptionService.subscribe(request));
    }

    /**
     * 종목 구독 해지 (조회 종료)
     * 사용자가 특정 종목 조회를 종료할 때 호출합니다.
     */
    @Operation(summary = "종목 구독 해지", description = "사용자가 종목 조회를 종료합니다.")
    @DeleteMapping("/{stockCode}")
    public ResponseEntity<Void> unsubscribe(@PathVariable String stockCode) {
        stockSubscriptionService.unsubscribe(stockCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * 내 구독 종목 목록 조회
     * 현재 로그인한 사용자가 구독 중인 종목 목록을 조회합니다.
     */
    @Operation(summary = "내 구독 종목 목록", description = "현재 구독 중인 종목 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<StockSubscriptionDTO.SubscriptionResponse>> getMySubscriptions() {
        return ResponseEntity.ok(stockSubscriptionService.getMySubscriptions());
    }

    /**
     * 특정 종목의 구독자 수 조회
     * 관리자나 통계 목적으로 사용합니다.
     */
    @Operation(summary = "종목 구독자 수", description = "특정 종목을 구독 중인 사용자 수를 조회합니다.")
    @GetMapping("/{stockCode}/count")
    public ResponseEntity<Long> getSubscriberCount(@PathVariable String stockCode) {
        return ResponseEntity.ok(stockSubscriptionService.getSubscriberCount(stockCode));
    }
}
