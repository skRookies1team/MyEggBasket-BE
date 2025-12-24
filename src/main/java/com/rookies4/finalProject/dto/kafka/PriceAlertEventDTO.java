package com.rookies4.finalProject.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * price-alert-events 토픽으로 발행하는 목표가 알림 이벤트 DTO
 * WS 전송 파드가 이 이벤트를 받아서 클라이언트에게 알림을 전송합니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceAlertEventDTO {
    
    // 이벤트 고유 ID (UUID)
    private String eventId;
    
    // 사용자 ID
    private Long userId;
    
    // 종목 코드
    private String stockCode;
    
    // 종목명
    private String stockName;
    
    //  설정된 목표가
    private BigDecimal triggerPrice;
    
    //  현재가 (목표가 만족 시점의 가격)
    private BigDecimal currentPrice;
    
    // 알림 타입: "UPPER" (상승 목표가 도달) 또는 "LOWER" (하락 목표가 도달)
    private AlertType alertType;
    
    // 이벤트 타입: "TRIGGERED" (목표가 도달), "SET" (목표가 설정), "CANCELED" (목표가 취소)
    private EventType eventType;
    
    // 이벤트 발생 시간
    private LocalDateTime timestamp;
    
    public enum AlertType {
        UPPER,  // 상승 목표가 도달 (매도 신호)
        LOWER   // 하락 목표가 도달 (매수 신호)
    }
    
    public enum EventType {
        TRIGGERED,  // 목표가 도달 (실시간 체결 데이터에 의해)
        SET,        // 목표가 설정
        CANCELED    // 목표가 취소
    }
}
