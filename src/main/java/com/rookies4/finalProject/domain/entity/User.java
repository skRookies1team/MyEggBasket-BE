package com.rookies4.finalProject.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    // user_id(BIGSERIAL PRIMARY KEY)에 매핑
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    // email(VARCHAR(100))에 매핑
    // 이메일
    @Email
    @Column(name = "email", length = 100, nullable = false, unique=true)
    private String email;

    // username(VARCHAR(50))에 매핑.
    // 사용자 이름
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    // password(VARCHAR(255) NOT NULL)에 매핑
    // 비밀번호
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    // appkey(VARCHAR(255))에 매핑 - 증권사 API Key (암호화 필수)
    // 증권사 API Key
    @Column(name = "appkey", length = 255)
    private String appkey;

    // appsecret(VARCHAR(255))에 매핑 - 증권사 API Secret (암호화 필수)
    // 증권사 API Secret
    @Column(name = "appsecret", length = 255)
    private String appsecret;

    // fcm_token(VARCHAR(255))에 매핑
    // FCM 토큰
    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    // created_at(TIMESTAMP DEFAULT CURRENT_TIMESTAMP)에 매핑
    // 생성 시각
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // updated_at(TIMESTAMP DEFAULT CURRENT_TIMESTAMP)에 매핑
    // 최종 수정 시각
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- Life Cycle Callback: 생성 및 수정 시각 자동 주입 ---
    // 엔티티가 영속화되기 전에 실행
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 엔티티가 업데이트되기 전에 실행
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}