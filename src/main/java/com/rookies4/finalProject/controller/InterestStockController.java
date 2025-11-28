package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.InterestStockDTO;
import com.rookies4.finalProject.service.InterestStockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app/users/watchlist")
@RequiredArgsConstructor
public class InterestStockController {
    private final InterestStockService interestStockService;

    //1. 관심 종목 추가
    @PostMapping
    public ResponseEntity<InterestStockDTO.InterestStockResponse> addInterestStock(@Valid @RequestBody InterestStockDTO.InterestStockRequest request){
       return ResponseEntity.status(HttpStatus.CREATED).body(interestStockService.addInterestStock(request));
    }

    //2. 관심 종목 조회
    @GetMapping()
    public ResponseEntity<List<InterestStockDTO.InterestStockResponse>> showInterestStock(){
        return ResponseEntity.ok(interestStockService.showInterestStock());
    }

    //3. 관심 종목 삭제
    @DeleteMapping("/{stockCode}")
    public ResponseEntity<Void> deleteInterestStock(@PathVariable String stockCode){
        interestStockService.deleteInterestStock(stockCode);
        return ResponseEntity.noContent().build();
    }
}
