package com.rookies4.finalProject.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * subscription-events 토픽으로 발행하는 종목 구독/해지 이벤트 DTO
 * StockCollector가 이 이벤트를 받아서 구독을 동적으로 조절합니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionEventDTO {
    
    /**
     * 이벤트 타입: "SUBSCRIBE" (구독) 또는 "UNSUBSCRIBE" (구독 해지)
     */
    private EventType eventType;
    
    /**
     * 사용자 ID
     */
    private Long userId;
    
    /**
     * 종목 코드
     */
    private String stockCode;
    
    /**
     * 이벤트 발생 시간
     */
    private LocalDateTime timestamp;
    
    public enum EventType {
        SUBSCRIBE,    // 종목 구독
        UNSUBSCRIBE   // 종목 구독 해지
    }
}
