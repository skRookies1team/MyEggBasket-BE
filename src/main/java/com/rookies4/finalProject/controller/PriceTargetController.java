package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.PriceTargetDTO;
import com.rookies4.finalProject.service.PriceTargetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 목표가 설정/관리 컨트롤러
@Tag(name = "Price Target", description = "목표가 설정/관리 API")
@RestController
@RequestMapping("/api/app/price-targets")
@RequiredArgsConstructor
public class PriceTargetController {

    private final PriceTargetService priceTargetService;

    // 상한가 설정 (1개만 허용)
    @Operation(summary = "상한가 설정", description = "종목의 상한가를 설정합니다 (1개만 허용, 기존 값은 덮어쓰기)")
    @PostMapping("/upper")
    public ResponseEntity<PriceTargetDTO.PriceTargetResponse> setUpperTarget(
            @Valid @RequestBody PriceTargetDTO.SetTargetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(priceTargetService.setUpperTarget(request));
    }

    // 하한가 설정 (1개만 허용)
    @Operation(summary = "하한가 설정", description = "종목의 하한가를 설정합니다 (1개만 허용, 기존 값은 덮어쓰기)")
    @PostMapping("/lower")
    public ResponseEntity<PriceTargetDTO.PriceTargetResponse> setLowerTarget(
            @Valid @RequestBody PriceTargetDTO.SetTargetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(priceTargetService.setLowerTarget(request));
    }

    // 상한가 취소
    @Operation(summary = "상한가 취소", description = "종목의 상한가를 취소합니다")
    @DeleteMapping("/{stockCode}/upper")
    public ResponseEntity<Void> clearUpperTarget(@PathVariable String stockCode) {
        priceTargetService.clearUpperTarget(stockCode);
        return ResponseEntity.noContent().build();
    }

    // 하한가 취소
    @Operation(summary = "하한가 취소", description = "종목의 하한가를 취소합니다")
    @DeleteMapping("/{stockCode}/lower")
    public ResponseEntity<Void> clearLowerTarget(@PathVariable String stockCode) {
        priceTargetService.clearLowerTarget(stockCode);
        return ResponseEntity.noContent().build();
    }

    // 내 목표가 목록 조회
    @Operation(summary = "내 목표가 목록", description = "현재 설정한 목표가 목록을 조회합니다")
    @GetMapping
    public ResponseEntity<List<PriceTargetDTO.PriceTargetResponse>> getMyPriceTargets() {
        return ResponseEntity.ok(priceTargetService.getMyPriceTargets());
    }

    // 목표가 조회 (종목별)
    @Operation(summary = "목표가 조회", description = "특정 종목의 목표가를 조회합니다")
    @GetMapping("/{stockCode}")
    public ResponseEntity<PriceTargetDTO.PriceTargetResponse> getPriceTarget(@PathVariable String stockCode) {
        return ResponseEntity.ok(priceTargetService.getPriceTarget(stockCode));
    }
}
