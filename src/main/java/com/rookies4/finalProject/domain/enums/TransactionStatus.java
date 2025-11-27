package com.rookies4.finalProject.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TransactionStatus {
    PENDING("PENDING", "미체결"), // 주문 접수 후 대기 중
    COMPLETED("COMPLETED", "체결"), // 거래 완료
    CANCELLED("CANCELLED", "취소"); // 주문 취소

    private final String code;
    private final String description;
}
