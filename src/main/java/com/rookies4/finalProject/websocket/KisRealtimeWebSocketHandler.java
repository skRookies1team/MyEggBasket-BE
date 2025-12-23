package com.rookies4.finalProject.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.dto.RealtimePriceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class KisRealtimeWebSocketHandler extends TextWebSocketHandler {

    private final String approvalKey;
    private final String custType;
    private final RealtimePriceBroadcaster broadcaster;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KisRealtimeWebSocketHandler(
            String approvalKey,
            String custType,
            RealtimePriceBroadcaster broadcaster
    ) {
        this.approvalKey = approvalKey;
        this.custType = custType;
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[KIS] WebSocket connected. sessionId={}", session.getId());
    }

    public void sendSubscribe(WebSocketSession session, String stockCode) {
        sendReg(session, "1", stockCode); // 1: 등록
    }

    public void sendUnsubscribe(WebSocketSession session, String stockCode) {
        sendReg(session, "2", stockCode); // 2: 해제
    }

    private void sendReg(WebSocketSession session, String trType, String stockCode) {
        if (session == null || !session.isOpen()) return;

        try {
            Map<String, Object> header = new HashMap<>();
            header.put("approval_key", approvalKey);
            header.put("custtype", custType);
            header.put("tr_type", trType);
            header.put("content-type", "utf-8");

            Map<String, Object> input = new HashMap<>();
            input.put("tr_id", "H0STCNT0");
            input.put("tr_key", stockCode);

            Map<String, Object> body = new HashMap<>();
            body.put("input", input);

            Map<String, Object> msg = new HashMap<>();
            msg.put("header", header);
            msg.put("body", body);

            String json = objectMapper.writeValueAsString(msg);
            log.info("[KIS] {} 요청 전송: {}", "1".equals(trType) ? "구독" : "해제", json);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("[KIS] reg send failed. trType={}, stockCode={}", trType, stockCode, e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload().trim();
        log.info("[KIS] 수신 원문: {}", payload);

        if (payload.contains("PINGPONG")) {
            session.sendMessage(new TextMessage(payload));
            return;
        }

        if (payload.startsWith("{")) {
            log.debug("[KIS] 구독 응답(JSON): {}", payload);
            return;
        }

        if (!payload.startsWith("0|H0STCNT0")) return;

        String[] parts = payload.split("\\|");
        if (parts.length < 4) return;

        String data = parts[3];
        String[] f = data.split("\\^");

        if (f.length < 32) {
            log.warn("[KIS] 필드 길이 부족: {} fields", f.length);
            return;
        }

        // ✅ 멀티 종목이면 종목코드는 payload 안에 들어있음 (f[0])
        String stockCode = f[0];

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
                stockCode, dto.getPrice(), dto.getDiff(), dto.getDiffRate());

        broadcaster.send(dto);
    }

    private long toLong(String v) {
        try { return Long.parseLong(v.trim()); }
        catch (Exception e) { return 0L; }
    }

    private double toDouble(String v) {
        try { return Double.parseDouble(v.trim()); }
        catch (Exception e) { return 0.0; }
    }
}