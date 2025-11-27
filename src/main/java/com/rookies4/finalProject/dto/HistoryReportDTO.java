package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rookies4.finalProject.domain.entity.HistoryReport;
import com.rookies4.finalProject.domain.entity.Portfolio;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class HistoryReportDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HistoryReportRequest{
        private Long portfolioId;
        private float totalReturnRate;
        private float successRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HistoryReportResponse{
        private Long portfolioId;
        private float totalReturnRate;
        private float successRate;

        public static HistoryReportDTO.HistoryReportResponse fromEntity(HistoryReport historyReport){
            return HistoryReportResponse.builder()
                    .portfolioId(historyReport.getHistoryId())
                    .totalReturnRate(historyReport.getTotalReturnRate())
                    .successRate((historyReport.getSuccessRate()))
                    .build();
        }
    }
}
