package com.rookies4.finalProject.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.dto.RealtimePriceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class KisRealtimeWebSocketHandler extends TextWebSocketHandler {

    private final String stockCode;
    private final String approvalKey;
    private final String custType; // 고객 타입 (B: 법인, P: 개인)
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RealtimePriceBroadcaster broadcaster;

    public KisRealtimeWebSocketHandler(
            String stockCode,
            String approvalKey,
            String custType,
            RealtimePriceBroadcaster broadcaster
    ) {
        this.stockCode = stockCode;
        this.approvalKey = approvalKey;
        this.custType = custType;
        this.broadcaster = broadcaster;
    }

    // 웹소켓 연결 성공 후 KIS에 응답 요청 보내는 메서드
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        // 요청 헤더 설정
        Map<String, Object> requestHeader = new HashMap<>();
        requestHeader.put("approval_key", approvalKey);
        requestHeader.put("custtype", custType);
        requestHeader.put("tr_type", "1"); // 1: 등록
        requestHeader.put("content-type", "utf-8");

        // 요청 바디 설정
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("tr_id", "H0STCNT0");
        requestBody.put("tr_key", stockCode);

        // 요청 메시지 설정
        Map<String, Object> requestMessage = new HashMap<>();
        requestMessage.put("header", requestHeader);
        requestMessage.put("body", requestBody);

        String json = objectMapper.writeValueAsString(requestMessage);
        log.info("[KIS] 구독 요청 전송: {}", json);
        session.sendMessage(new TextMessage(json)); // 요청 전송
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        String payload = message.getPayload();

        // PINGPONG 처리
        if (payload.contains("PINGPONG")) {
            log.debug("[KIS] PINGPONG 수신 -> 응답 전송");
            session.sendMessage(new TextMessage("PINGPONG"));
            return;
        }

        // JSON 응답 (구독 성공 메시지)
        if (payload.startsWith("{")) {
            log.debug("[KIS] 구독 응답(JSON): {}", payload);
            return;
        }

        // 실시간 체결 데이터
        if (!payload.startsWith("0|H0STCNT0")) {
            log.debug("[KIS] 기타 응답: {}", payload);
            return;
        }

        String[] parts = payload.split("\\|");
        if (parts.length < 4) return;

        String data = parts[3];
        String[] f = data.split("\\^");

        // 최소 길이 체크
        if (f.length < 32) { // TOTAL_ASKP_RSQN, TOTAL_BIDP_RSQN까지 포함하려면 32 이상 필요
            log.warn("[KIS] 필드 길이 부족: {} fields", f.length);
            return;
        }

        // DTO로 매핑
        RealtimePriceDTO dto = RealtimePriceDTO.builder()
                .stockCode(stockCode) // KIS 응답 말고 파라미터로 전달받은 종목코드 사용
                .tickTime(f[1])                           // 체결 시간
                .price(toDecimal(f[2]))                   // 현재가
                .diff(toDecimal(f[4]))                    // 전일 대비
                .diffRate(toDecimal(f[5]))                // 전일 대비율
                .sellCount(toDecimal(f[15]))              // 매도 체결 건수
                .buyCount(toDecimal(f[16]))               // 매수 체결 건수
                .askPrice(toDecimal(f[10]))               // 매도호가1
                .bidPrice(toDecimal(f[11]))               // 매수호가1
                .totalAskQuantity(toDecimal(f[30]))       // 총 매도호가 잔량
                .totalBidQuantity(toDecimal(f[31]))       // 총 매수호가 잔량
                .openPrice(toDecimal(f[7]))               // 시가
                .highPrice(toDecimal(f[8]))               // 고가
                .lowPrice(toDecimal(f[9]))                // 저가
                .volume(toDecimal(f[13]))                 // 누적 거래량
                .tradingValue(toDecimal(f[14]))           // 누적 거래대금
                .build();

        log.info("[{}] 현재가={} (전일대비={} / {}%)",
                stockCode,
                dto.getPrice(),
                dto.getDiff(),
                dto.getDiffRate()
        );

        // 프론트로 응답 전달
        broadcaster.send(dto);
    }

    private BigDecimal toDecimal(String v) {
        try {
            return new BigDecimal(v.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}