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
        Map<String, Object> header = new HashMap<>();
        header.put("approval_key", approvalKey);
        header.put("custtype", custType);
        header.put("tr_type", "1"); // 1: 등록
        header.put("content-type", "utf-8");

        // 요청 바디 설정
        Map<String, Object> input = new HashMap<>();
        input.put("tr_id", "H0STCNT0");
        input.put("tr_key", stockCode);

        Map<String, Object> body = new HashMap<>();
        body.put("input", input);

        // 요청 메시지 설정
        Map<String, Object> msg = new HashMap<>();
        msg.put("header", header);
        msg.put("body", body);

        String json = objectMapper.writeValueAsString(msg);
        log.info("[KIS] 구독 요청 전송: {}", json);
        session.sendMessage(new TextMessage(json)); // 요청 전송
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        String payload = message.getPayload().trim();
        log.info("[KIS] 수신 원문: {}", payload);

        // PINGPONG 처리
        if (payload.contains("PINGPONG")) {
            log.debug("[KIS] PINGPONG 수신 -> 응답 전송");
            session.sendMessage(new TextMessage(payload));
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
                .stockCode(stockCode)
                .tickTime(f[1])
                .price(toLong(f[2]))
                .diff(toLong(f[4]))
                .diffRate(toDouble(f[5]))
                .sellCount(toLong(f[15]))
                .buyCount(toLong(f[16]))
                .askPrice(toLong(f[10]))
                .bidPrice(toLong(f[11]))
                .totalAskQuantity(toLong(f[30]))
                .totalBidQuantity(toLong(f[31]))
                .openPrice(toLong(f[7]))
                .highPrice(toLong(f[8]))
                .lowPrice(toLong(f[9]))
                .volume(toLong(f[13]))
                .tradingValue(toLong(f[14]))
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

    private long toLong(String v) {
        try {
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private double toDouble(String v) {
        try {
            return Double.parseDouble(v.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}