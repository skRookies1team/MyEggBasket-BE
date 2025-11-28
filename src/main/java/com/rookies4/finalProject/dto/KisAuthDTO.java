package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * KIS API 인증 관련 DTO
 */
public class KisAuthDTO {

    /**
     * KIS API 토큰 발급 응답 DTO
     * KIS API는 snake_case를 사용하므로 @JsonProperty로 매핑
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KisTokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
        
        @JsonProperty("token_type")
        private String tokenType;
        
        @JsonProperty("expires_in")
        private Long expiresIn;
        
        private String scope;
        
        @JsonProperty("access_token_expired")
        private String accessTokenExpired;
    }
}
