package com.rookies4.finalProject.websocket;

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

    public void send(RealtimePriceDTO dto) {
        String destination = "/topic/realtime-price/" + dto.getStockCode();

        log.debug("[Realtime] send → {}, price={}, diff={}, diffRate={}",
                destination,
                dto.getPrice(),
                dto.getDiff(),
                dto.getDiffRate()
        );

        messagingTemplate.convertAndSend(destination, dto);
    }
}
