package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.HistoryReport;
import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.dto.HistoryReportDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.HistoryReportRepository;
import com.rookies4.finalProject.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class HistoryReportService {
    private final HistoryReportRepository historyReportRepository;
    private final PortfolioRepository portfolioRepository;

    //1. History Report 생성
    public HistoryReportDTO.HistoryReportResponse createHistoryReport(HistoryReportDTO.HistoryReportRequest request){
        Portfolio portfolio = portfolioRepository.findById(request.getPortfolioId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));
        HistoryReport historyReport = HistoryReport.builder()
                .portfolio(portfolio)
                .totalReturnRate(request.getTotalReturnRate())
                .successRate(request.getSuccessRate())
                .build();

        return HistoryReportDTO.HistoryReportResponse.fromEntity(historyReportRepository.save(historyReport));
    }

    //2. portfolio별 History Report 조회
    public List<HistoryReportDTO.HistoryReportResponse> readHistoryReport(Long portfolioId){
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        return historyReportRepository.findByPortfolio(portfolio)
                .stream()
                .map(HistoryReportDTO.HistoryReportResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
