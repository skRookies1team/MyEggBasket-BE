package com.rookies4.finalProject.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rookies4.finalProject.domain.entity.BubbleChartSnapshot;
import com.rookies4.finalProject.dto.AIBubbleChartDTO;
import com.rookies4.finalProject.repository.BubbleChartSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AIBubbleChartService {

    private final AtomicReference<Map<String, AIBubbleChartDTO.PeriodSummary>> latestPayload =
            new AtomicReference<>(Collections.emptyMap());

    private final BubbleChartSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public AIBubbleChartDTO.TrendResponse upsertTrends(AIBubbleChartDTO.TrendUpsertRequest request) {
        Map<String, AIBubbleChartDTO.PeriodSummary> sanitized = sanitize(request == null ? null : request.getPeriods());
        latestPayload.set(sanitized);

        try {
            String json = objectMapper.writeValueAsString(sanitized);
            BubbleChartSnapshot snapshot = BubbleChartSnapshot.builder()
                    .payloadJson(json)
                    .build();
            snapshotRepository.save(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("버블차트 스냅샷 저장 중 오류가 발생했습니다.", e);
        }

        return AIBubbleChartDTO.TrendResponse.of(sanitized);
    }

    @Transactional(readOnly = true)
    public AIBubbleChartDTO.TrendResponse getLatestTrends() {
        Map<String, AIBubbleChartDTO.PeriodSummary> current = latestPayload.get();
        if (current != null && !current.isEmpty()) {
            return AIBubbleChartDTO.TrendResponse.of(current);
        }

        return snapshotRepository.findTopByOrderByCreatedAtDesc()
                .map(snapshot -> {
                    try {
                        Map<String, AIBubbleChartDTO.PeriodSummary> payload = objectMapper.readValue(
                                snapshot.getPayloadJson(),
                                new TypeReference<Map<String, AIBubbleChartDTO.PeriodSummary>>() {}
                        );
                        latestPayload.set(payload);
                        return AIBubbleChartDTO.TrendResponse.of(payload);
                    } catch (Exception e) {
                        throw new IllegalStateException("버블차트 스냅샷 로드 중 오류가 발생했습니다.", e);
                    }
                })
                .orElseGet(() -> AIBubbleChartDTO.TrendResponse.of(Collections.emptyMap()));
    }

    private Map<String, AIBubbleChartDTO.PeriodSummary> sanitize(Map<String, AIBubbleChartDTO.PeriodSummary> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }

        return source.entrySet().stream()
                .filter(entry -> Objects.nonNull(entry.getKey()) && Objects.nonNull(entry.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }
}
