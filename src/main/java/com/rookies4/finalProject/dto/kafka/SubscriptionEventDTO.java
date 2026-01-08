package com.rookies4.finalProject.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionEventDTO {

    private EventType eventType;

    /**
     * 구독 상세 타입: "VIEW"(조회용), "INTEREST"(관심종목용) 등
     * Python SC에서 이 값을 보고 관리자 계정/사용자 계정 중 어디에 할당할지 결정합니다.
     */
    private String subType;

    private Long userId;
    private String stockCode;
    private LocalDateTime timestamp;

    public enum EventType {
        SUBSCRIBE,
        UNSUBSCRIBE
    }
}