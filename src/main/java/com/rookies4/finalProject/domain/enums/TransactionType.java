package com.rookies4.finalProject.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TransactionType {
    BUY("BUY", "매수"),
    SELL("SELL", "매도");

    private final String code;
    private final String description;
}