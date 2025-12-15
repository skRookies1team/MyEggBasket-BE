package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.UserDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // --- 1. Create User (회원가입) ---
    @Transactional
    public UserDTO.UserResponse createUser(UserDTO.SignUpRequest request) {

        // 1. 비밀번호 확인 검증
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }

        // 2. 이메일 중복 체크 (DB 유니크 제약조건을 따름)
        if (userRepository.existsByEmail(request.getEmail())) {
            // 참고: ErrorCode.USER_ID_DUPLICATE 대신 ErrorCode.EMAIL_DUPLICATE와 같은 코드가 더 명확합니다.
            throw new BusinessException(ErrorCode.USER_ID_DUPLICATE, "이미 사용 중인 이메일입니다.");
        }

        // 3. 비밀번호, Appkey, AppSecret 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        String encodedAppkey = encodeBase64(request.getAppkey());
        String encodedAppsecret = encodeBase64(request.getAppsecret());

        // 4. User 엔티티 생성
        User user = User.builder()
                .email(request.getEmail())
                .password(encodedPassword) // 암호화된 비밀번호 저장
                .username(request.getUsername())
                .appkey(encodedAppkey) // Base64 인코딩된 appkey 저장
                .appsecret(encodedAppsecret) // Base64 인코딩된 appsecret 저장
                .account(request.getAccount())
                .build();

        // 5. DB 저장 및 응답 DTO 변환
        return UserDTO.UserResponse.fromEntity(userRepository.save(user));
    }

    // --- 2. Read User (사용자 조회) ---
    /**
     * ID를 기반으로 사용자를 조회합니다. (인증 후 내 정보 조회 등에 사용)
     * @param userId 조회할 사용자 ID
     * @return User 응답 DTO
     */
    @Transactional(readOnly = true)
    public UserDTO.UserResponse readUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "해당 ID의 사용자를 찾을 수 없습니다."));

        return UserDTO.UserResponse.fromEntity(user);
    }

    /**
     * Email을 기반으로 User 엔티티를 조회합니다. (Spring Security UserDetailsService에서 사용)
     * @param email 조회할 사용자 이메일
     * @return User 엔티티 (Optional 처리)
     */
    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "해당 이메일의 사용자를 찾을 수 없습니다."));
    }

    /**
     * ID를 기반으로 User 엔티티를 조회합니다.
     * @param userId 조회할 사용자 ID
     * @return User 엔티티 (Optional 처리)
     */
    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "해당 ID의 사용자를 찾을 수 없습니다."));
    }

    // --- 3. Update User (사용자 정보 수정) ---
    /**
     * 사용자의 이름, AppKey, AppSecret, FCM Token 등의 정보를 수정합니다. (비밀번호/이메일 제외)
     * @param userId 수정할 사용자 ID
     * @param request 수정 요청 DTO (UserDTO.UpdateRequest 필요)
     * @return 수정된 User 응답 DTO
     */
    @Transactional
    public UserDTO.UserResponse updateUser(Long userId, UserDTO.UpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "수정하려는 사용자를 찾을 수 없습니다."));

        // DTO 필드 값이 null이 아닐 경우에만 업데이트 (부분 업데이트)
        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }
        if (request.getAppkey() != null) {
            String encodedAppkey = encodeBase64(request.getAppkey());
            user.setAppkey(encodedAppkey);
        }
        if (request.getAppsecret() != null) {
            String encodedAppsecret = encodeBase64(request.getAppsecret());
            user.setAppsecret(encodedAppsecret);
        }
        if(request.getPassword().equals(user.getPassword())){
            String encodedPassword = passwordEncoder.encode(request.getNewPassword());
            user.setPassword(encodedPassword);
        }

        // FcmToken, RiskProfileScore 등 다른 필드도 업데이트 로직 추가 가능

        // save()를 명시적으로 호출하지 않아도 @Transactional에 의해 변경 내용이 반영됩니다 (Dirty Checking).
        // 하지만 가독성을 위해 명시적으로 save를 호출할 수도 있습니다.
        // userRepository.save(user);

        return UserDTO.UserResponse.fromEntity(user);
    }

    // --- 4. Delete User (사용자 삭제) ---
    /**
     * ID를 기반으로 사용자를 삭제합니다.
     * @param userId 삭제할 사용자 ID
     */
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "삭제하려는 사용자를 찾을 수 없습니다.");
        }
        userRepository.deleteById(userId);
    }

    /**
     * Base64로 문자열을 인코딩합니다.
     * @param plainText 인코딩할 문자열 (null일 수 있음)
     * @return Base64로 인코딩된 문자열 (입력이 null이면 null 반환)
     */
    private String encodeBase64(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        return Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
    }
}