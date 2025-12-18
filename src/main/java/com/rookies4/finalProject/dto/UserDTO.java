package com.rookies4.finalProject.dto;

import com.rookies4.finalProject.domain.entity.User;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class UserDTO {

    // --- 1. 회원가입 요청
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SignUpRequest {

        @NotBlank(message = "이메일은 필수 입력 항목입니다.")
        private String email;

        @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
        private String password;

        @NotBlank(message = "비밀번호 확인은 필수 입력 항목입니다.")
        private String confirmPassword;

        @NotBlank(message = "사용자 이름은 필수 입력 항목입니다.")
        private String username;

        @NotBlank(message = "KIS appkey를 입력해주세요")
        private String appkey;

        @NotBlank(message = "KIS appsecret을 입력해주세요")
        private String appsecret;

        @NotBlank(message = "한국투자증권 계좌번호 앞 8자리를 입력해주세요")
        private String account;

    }

    // --- 2. 로그인 요청 
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoginRequest {

        @NotBlank(message = "이메일은 필수 입력 항목입니다.")
        private String email;

        @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
        private String password;
    }


    // --- 3. 사용자 정보 응답 
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserResponse {
        private Long id;
        private String email;
        private String username;
        // appkey, appsecret 필드 제거
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        // Entity를 DTO로 변환하는 생성자
        public static UserResponse fromEntity(User user) {
            return UserResponse.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .username(user.getUsername())
                    // appkey, appsecret 매핑 제거
                    .createdAt(user.getCreatedAt())
                    .updatedAt(user.getUpdatedAt())
                    .build();
        }
    }

    // --- 4. 사용자 정보 수정 요청 
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        // 이름은 null일 경우 수정하지 않도록 Optional 필드로 간주
        private String username;
        private String password;
        private String newPassword;
        private String appkey;
        private String appsecret;
        // 다른 수정 가능한 필드 추가 가능 (예: fcmToken)
    }

    // --- 5. 로그인 응답 
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoginResponse {
        private String accessToken;
        private String tokenType = "Bearer";
        private UserResponse user;
    }
}
