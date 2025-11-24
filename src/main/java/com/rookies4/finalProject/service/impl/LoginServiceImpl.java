package com.rookies4.finalProject.service.impl;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.UserDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.JwtTokenProvider;
import com.rookies4.finalProject.service.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LoginServiceImpl implements LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(readOnly = true)
    public UserDTO.LoginResponse login(UserDTO.LoginRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "이메일 또는 비밀번호가 올바르지 않습니다."));

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        // 3. JWT 토큰 생성
        String accessToken = jwtTokenProvider.createToken(user.getId(), user.getEmail());

        // 4. 응답 DTO 생성
        return UserDTO.LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .user(UserDTO.UserResponse.fromEntity(user))
                .build();
    }
}
