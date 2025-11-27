package com.rookies4.finalProject.dto;

import com.rookies4.finalProject.domain.entity.StockRelation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class StockRelationDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockRelationRequest{
        private String fromStockTicker;
        private String toStockTicker;
        private String relationType;
        private float weight;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockRelationResponse{
        private String fromStockTicker;
        private String toStockTicker;
        private String relationType;
        private float weight;

        public static StockRelationDTO.StockRelationResponse fromEntity(StockRelation stockRelation){

            return StockRelationResponse.builder()
                    .fromStockTicker(stockRelation.getFromStock().getTicker())
                    .toStockTicker(stockRelation.getToStock().getTicker())
                    .relationType(stockRelation.getRelationType())
                    .weight(stockRelation.getWeight())
                    .build();

        }
    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockRelationUpdateRequest{
        private String relationType;
        private float weight;
    }
}
