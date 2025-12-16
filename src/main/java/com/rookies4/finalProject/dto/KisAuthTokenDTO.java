package com.rookies4.finalProject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rookies4.finalProject.domain.entity.KisAuthToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;

/**
 * KIS API 인증 관련 DTO
 */
public class KisAuthTokenDTO {

    /**
     * KIS API 토큰 발급 요청 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KisTokenRequest {
        private String grant_type;
        private String appkey;
        private String appsecret;
    }

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
        private String access_token;
        
        @JsonProperty("token_type")
        private String token_type;
        
        @JsonProperty("expires_in")
        private int expires_in;
        
        private String scope;
        
        @JsonProperty("access_token_token_expired")
        private String accessTokenExpired;

        // 엔티티 -> DTO 변환 메소드 추가
        public static KisTokenResponse fromEntity(KisAuthToken entity) {
            return KisTokenResponse.builder()
                    .access_token(entity.getAccessToken())
                    .token_type(entity.getTokenType())
                    .expires_in(entity.getExpiresIn())
                    // LocalDateTime을 String으로 변환
                    .accessTokenExpired(entity.getAccessTokenTokenExpired().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .build();
        }
        
        // DTO 필드명 변경에 따른 getter 추가 (서비스 코드 호환성 유지)
        public String getAccessToken() {
            return access_token;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KisApprovalKeyResponse{
        @JsonProperty("approval_key")
        private String approvalKey;
    }
}