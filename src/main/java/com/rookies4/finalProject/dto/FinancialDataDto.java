package com.rookies4.finalProject.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FinancialDataDto {
    private String corpName;
    private String stockCode;
    private String bsnsYear;
    private String reportName;
    private Long finRevenue;
    private Long finOpIncome;
    private Long finNetIncome;
    private Long finTotalAssets;
    private Long finTotalEquity;
    private Long finTotalLiabilities;
    private String divDpsCommon;
    private String empTotalCount;
    private Integer capitalChangeCount;
    private String corpCode;
    private String rceptNo;
    private String rceptDt;
    private String reprtCode;
    private String treasuryStockEvent;
}