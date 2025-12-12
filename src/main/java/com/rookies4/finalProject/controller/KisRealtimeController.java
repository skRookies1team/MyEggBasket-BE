package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/kis/realtime")
@RequiredArgsConstructor
public class KisRealtimeController {

    private final RealtimeService kisRealtimeService;

    @GetMapping("/price/{stockCode}")
    public ResponseEntity<Void> subscribeRealtimePrice(
            @PathVariable String stockCode,
            @RequestParam(name = "virtual", defaultValue = "false") boolean useVirtualServer) {
        // 실시간 체결가 가져오기
        kisRealtimeService.getRealtimePrice(useVirtualServer, stockCode);

        return ResponseEntity.accepted().build();
    }
}