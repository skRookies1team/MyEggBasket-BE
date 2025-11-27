package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.HistoryReportDTO;
import com.rookies4.finalProject.service.HistoryReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app/portfolio/history")
@RequiredArgsConstructor
public class HistoryReportController {

    private final HistoryReportService historyReportService;

    //1. History Report 생성
    @PostMapping
    public ResponseEntity<HistoryReportDTO.HistoryReportResponse> createHistoryReport(@RequestBody  HistoryReportDTO.HistoryReportRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body((historyReportService.createHistoryReport(request)));
    }

    //2. History Report 조회
    @GetMapping("/{portfolioId}")
    public ResponseEntity<List<HistoryReportDTO.HistoryReportResponse>> readHistoryReport(@PathVariable Long portfolioId){
        return ResponseEntity.ok(historyReportService.readHistoryReport(portfolioId));
    }
}
