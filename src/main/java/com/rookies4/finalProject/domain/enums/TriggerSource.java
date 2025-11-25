package com.rookies4.finalProject.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 거래 주문 원천 타입
 * 사용자의 수동 주문인지, AI 자동 추천에 의한 주문인지 구분
 */

@Getter
@AllArgsConstructor
public enum TriggerSource {
    MANUAL("MANUAL", "수동 주문", "사용자가 직접 실행한 주문"),
    AI("AI", "AI 주문", "AI 추천에 따른 주문");

    private final String code;
    private final String name;
    private final String description;
}