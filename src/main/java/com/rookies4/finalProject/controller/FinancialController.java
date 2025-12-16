package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.FinancialDataDto;
import com.rookies4.finalProject.service.FinancialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/app/financial")
@RequiredArgsConstructor
public class FinancialController {

    private final FinancialService financialService;

    @GetMapping("/data")
    public ResponseEntity<List<FinancialDataDto>> getAllData() {
        List<FinancialDataDto> data = financialService.getFinancialData();
        return ResponseEntity.ok(data);
    }
}