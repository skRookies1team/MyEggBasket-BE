package com.rookies4.finalProject.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * KIS API 요청을 위한 Request DTO
 */
@Getter
@Builder
public class KisApiRequest {
    private String path;
    private String trId;
    @Builder.Default
    private Map<String, String> queryParams = new HashMap<>();
    private Object body;
    private boolean useVirtualServer;

    public static class KisApiRequestBuilder {
        private Map<String, String> queryParams$value;
        private boolean queryParams$set;

        public KisApiRequestBuilder param(String key, String value) {
            if (!this.queryParams$set) {
                this.queryParams$value = new HashMap<>();
                this.queryParams$set = true;
            }
            this.queryParams$value.put(key, value);
            return this;
        }
    }
}
