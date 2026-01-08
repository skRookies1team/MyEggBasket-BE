package com.rookies4.finalProject.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceAlertMessageDTO {

    // 프론트 식별용
    private String alertId;

    // 사용자
    private Long userId;

    // 종목
    private String stockCode;
    private String stockName;

    // 어떤 목표가인지
    private AlertType alertType;

    // === Entity 기준 데이터 ===

    // 설정된 목표가 (upperTarget 또는 lowerTarget)
    private BigDecimal targetPrice;

    // 목표가 도달 시점의 현재가
    private BigDecimal currentPrice;

    // Entity에 기록된 트리거 시각
    private LocalDateTime triggeredAt;

    public enum AlertType {
        UPPER,
        LOWER
    }
}