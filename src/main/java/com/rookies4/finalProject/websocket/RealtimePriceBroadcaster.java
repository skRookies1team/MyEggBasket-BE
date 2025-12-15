package com.rookies4.finalProject.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.dto.RealtimePriceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimePriceBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void send(RealtimePriceDTO dto) {
        String destination = "/topic/realtime-price/" + dto.getStockCode();

        log.info("[Realtime] send -> {}, payload={}",
                destination,
                safeJson(dto));

        messagingTemplate.convertAndSend(destination, dto);
    }

    private String safeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception e) { return String.valueOf(o); }
    }
}
