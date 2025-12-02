package com.rookies4.finalProject.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class KisStockOrderDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KisStockOrderResponse{
        private String success;
        private String responseCode;
        private String responseMsg;
        private String description;
        private String OrderNo;
        private String OrderTime;
    }
}
