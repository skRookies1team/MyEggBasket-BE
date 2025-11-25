package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.PortfolioDTO;
import com.rookies4.finalProject.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app/portfolios")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioService portfolioService;

    //1. 포트폴리오 추가
    @PostMapping
    public ResponseEntity<PortfolioDTO.PortfolioResponse> createPortfolio(@Valid @RequestBody PortfolioDTO.PortfolioRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(portfolioService.createPortfolio(request));
    }
    //2. 전체 포트폴리오 조회
    @GetMapping
    public ResponseEntity<List<PortfolioDTO.PortfolioResponse>> readPortfolios() {
        return ResponseEntity.ok(portfolioService.readPortfolios());
    }

    //3. 특정 포트폴리오 조회
    @GetMapping("/{portfolioId}")
    public ResponseEntity<PortfolioDTO.PortfolioResponse> readPortfolio(@PathVariable Long portfolioId){
        return ResponseEntity.ok(portfolioService.readPortfolio(portfolioId));
    }

    //4.포트폴리오 수정
    @PutMapping("/{portfolioId}")
    public ResponseEntity<PortfolioDTO.PortfolioResponse> updatePortfolio(@PathVariable Long portfolioId, @Valid @RequestBody PortfolioDTO.PortfolioRequest updateRequest){
        return ResponseEntity.ok(portfolioService.updatePortfolio(portfolioId,updateRequest));
    }
    //5.포트폴리오 삭제
    @DeleteMapping("/{portfolioId}")
    public ResponseEntity<Void> deletePortfolio(@PathVariable Long portfolioId){
        portfolioService.deletePortfolio(portfolioId);
        return ResponseEntity.noContent().build();
    }

    //6. 포트폴리오 보유종목 조회
    @GetMapping("/{portfolioId}/holdings")
    public ResponseEntity<PortfolioDTO.PortfolioHoldingResponse> readPortfolioHoldings(@PathVariable Long portfolioId){
        return ResponseEntity.ok(portfolioService.readPortfolioHoldings(portfolioId));
    }
}
