package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.AIBubbleChartDTO;
import com.rookies4.finalProject.service.AIBubbleChartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/ai/keywords")
@RequiredArgsConstructor
public class AIBubbleChartController {

    private final AIBubbleChartService bubbleChartTrendService;

    /**
     * AI 모듈에서 전달한 기간별 키워드/카테고리 언급량을 수신한다.
     */
    @PostMapping("/trending")
    public ResponseEntity<AIBubbleChartDTO.TrendResponse> upsertTrends(
            @Valid @RequestBody AIBubbleChartDTO.TrendUpsertRequest request) {
        return ResponseEntity.ok(bubbleChartTrendService.upsertTrends(request));
    }

    /**
     * 프론트엔드에서 바로 사용할 수 있도록 가장 최근 언급량 데이터를 반환한다.
     */
    @GetMapping("/trending")
    public ResponseEntity<AIBubbleChartDTO.TrendResponse> getLatestTrends() {
        return ResponseEntity.ok(bubbleChartTrendService.getLatestTrends());
    }
}
