package com.rookies4.finalProject.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RiskLevel {
    CONSERVATIVE("CONSERVATIVE", "보수적", "안정적인 투자 선호"),
    MODERATE("MODERATE", "중립적", "균형잡힌 투자 선호"),
    AGGRESSIVE("AGGRESSIVE", "공격적", "고위험 고수익 선호");

    private final String code;
    private final String name;
    private final String description;
}