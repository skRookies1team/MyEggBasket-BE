package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.StockDTO;
import com.rookies4.finalProject.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app/stocks")
@RequiredArgsConstructor
public class StockController {
    private final StockService stockService;

    //1. 주식 정보 추가
    @PostMapping("")
    public ResponseEntity<StockDTO.StockResponse> createStock(@Valid @RequestBody StockDTO.StockRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.createStock(request));
    }
    
    //2. 주식 정보 조회
    @GetMapping("/{stockCode}")
    public ResponseEntity<StockDTO.StockResponse> readStock(@PathVariable String stockCode){
        return ResponseEntity.ok(stockService.readStock(stockCode));
    }

    @GetMapping("/search")
    public ResponseEntity<List<StockDTO.StockResponse>> searchStocks(@RequestParam String keyword) {
        return ResponseEntity.ok(stockService.searchStocks(keyword));
    }
}
