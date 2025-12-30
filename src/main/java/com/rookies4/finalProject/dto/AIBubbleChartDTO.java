package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AIBubbleChartDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemCount {
        @NotBlank(message = "키워드명은 필수입니다.")
        private String name;

        @NotNull(message = "count는 필수입니다.")
        private Long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PeriodSummary {
        @JsonProperty("period_start")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate periodStart;

        @JsonProperty("period_end")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate periodEnd;

        @Builder.Default
        @Valid
        private List<ItemCount> keywords = new ArrayList<>();

        @Builder.Default
        @Valid
        private List<ItemCount> categories = new ArrayList<>();
    }

    @Getter
    @NoArgsConstructor
    public static class TrendUpsertRequest {
        private Map<String, PeriodSummary> periods = new LinkedHashMap<>();

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public TrendUpsertRequest(Map<String, PeriodSummary> periods) {
            if (periods != null) {
                this.periods.putAll(periods);
            }
        }

        @JsonAnySetter
        public void addPeriod(String key, PeriodSummary summary) {
            if (summary != null) {
                periods.put(key, summary);
            }
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrendResponse {
        @Builder.Default
        private Map<String, PeriodSummary> periods = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, PeriodSummary> getPeriods() {
            return periods;
        }

        public static TrendResponse of(Map<String, PeriodSummary> payload) {
            Map<String, PeriodSummary> copy = payload == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(payload);
            return TrendResponse.builder()
                    .periods(Collections.unmodifiableMap(copy))
                    .build();
        }
    }
}
