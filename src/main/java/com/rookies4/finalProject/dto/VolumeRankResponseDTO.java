package com.rookies4.finalProject.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 거래량 순위 조회를 프론트엔드에 전달하기 위한 DTO
 */
@Data
@Builder
public class VolumeRankResponseDTO {
    private int rank;
    private String code;
    private String name;
    private BigDecimal price;
    private BigDecimal change;
    private Double rate;
    private Long volume;
    private Long prevVolume; // 전일 거래량
    private Double turnover; // 거래량 회전율
}