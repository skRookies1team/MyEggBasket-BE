package com.rookies4.finalProject.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RecommendationAction {
    BUY("BUY", "매수 추천"),
    SELL("SELL", "매도 추천"),
    HOLD("HOLD", "보유 유지");

    private final String code;
    private final String description;
}