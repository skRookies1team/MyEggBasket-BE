package com.rookies4.finalProject.service.impl;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.UserDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.JwtTokenProvider;
import com.rookies4.finalProject.service.LoginService;
import com.rookies4.finalProject.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LoginServiceImpl implements LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EncryptionUtil encryptionUtil; // EncryptionUtil 주입

    @Override
    public UserDTO.LoginResponse login(UserDTO.LoginRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "이메일 또는 비밀번호가 올바르지 않습니다."));

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        // 3. 암호화 마이그레이션 (ECB -> GCM)
        migrateEncryption(user);

        // 4. JWT 토큰 생성
        String accessToken = jwtTokenProvider.createToken(user.getId(), user.getEmail());

        // 5. 응답 DTO 생성
        return UserDTO.LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .user(UserDTO.UserResponse.fromEntity(user))
                .build();
    }

    /**
     * 기존 ECB로 암호화된 appkey와 appsecret을 GCM으로 재암호화하여 업데이트합니다.
     * @param user 사용자 엔티티
     */
    private void migrateEncryption(User user) {
        try {
            String decryptedAppKey = encryptionUtil.decrypt(user.getAppkey());
            String reEncryptedAppKey = encryptionUtil.encrypt(decryptedAppKey);
            if (!user.getAppkey().equals(reEncryptedAppKey)) {
                log.info("사용자 ID {}의 appkey를 GCM으로 마이그레이션합니다.", user.getId());
                user.setAppkey(reEncryptedAppKey);
            }

            String decryptedAppSecret = encryptionUtil.decrypt(user.getAppsecret());
            String reEncryptedAppSecret = encryptionUtil.encrypt(decryptedAppSecret);
            if (!user.getAppsecret().equals(reEncryptedAppSecret)) {
                log.info("사용자 ID {}의 appsecret을 GCM으로 마이그레이션합니다.", user.getId());
                user.setAppsecret(reEncryptedAppSecret);
            }
        } catch (Exception e) {
            log.error("암호화 마이그레이션 중 오류 발생 (사용자 ID: {})", user.getId(), e);
            // 마이그레이션 실패가 로그인 실패로 이어지지 않도록 예외를 던지지 않음
        }
    }
}
