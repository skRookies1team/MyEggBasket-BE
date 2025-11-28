package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.StockRelationDTO;
import com.rookies4.finalProject.service.StockRelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app/stock-relations")
@RequiredArgsConstructor
public class StockRelationController {
   private final StockRelationService stockRelationService;

   //1. stockRelation 생성
    @PostMapping
    public ResponseEntity<StockRelationDTO.StockRelationResponse> createStockRelation(
            @RequestBody StockRelationDTO.StockRelationRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(stockRelationService.createStockRelation(request));
    }

    //2. stockRelation fromStock에 대해 조회
    @GetMapping("from/{stockCode}")
    public ResponseEntity<List<StockRelationDTO.StockRelationResponse>> readFromStockRelation(
            @PathVariable String stockCode){
        return ResponseEntity.ok(stockRelationService.readFromStockRelation(stockCode));
    }
    //3. stockRelation toStock에 대해 조회
    @GetMapping("to/{stockCode}")
    public ResponseEntity<List<StockRelationDTO.StockRelationResponse>> readToStockRelation(
            @PathVariable String stockCode){
        return ResponseEntity.ok(stockRelationService.readToStockRelation(stockCode));
    }
}
