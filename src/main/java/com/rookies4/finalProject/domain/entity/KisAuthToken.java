package com.rookies4.finalProject.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "kis_auth_token")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KisAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auth_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name="access_token",length=350)
    private String accessToken;

    @Column(name = "token_type", length = 20)
    private String tokenType;

    @Column(name = "expires_in")
    private int expiresIn; // Long -> int

    @Column(name = "approval_key", length = 500)
    private String approvalKey;

    @Column(name = "access_token_token_expired")
    private LocalDateTime accessTokenTokenExpired; // String -> LocalDateTime

    /**
     * 토큰 정보를 업데이트하는 메소드
     */
    public void updateToken(String accessToken, String tokenType, int expiresIn) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.accessTokenTokenExpired = LocalDateTime.now().plusSeconds(expiresIn);
    }
}