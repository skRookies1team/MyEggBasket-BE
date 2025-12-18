package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.UserDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
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
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionUtil encryptionUtil;

    // --- 1. Create User (회원가입) ---
    @Transactional
    public UserDTO.UserResponse createUser(UserDTO.SignUpRequest request) {

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.USER_ID_DUPLICATE, "이미 사용 중인 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // AES-256으로 암호화하여 저장
        String encryptedAppkey = encryptionUtil.encrypt(request.getAppkey());
        String encryptedAppsecret = encryptionUtil.encrypt(request.getAppsecret());

        User user = User.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .username(request.getUsername())
                .appkey(encryptedAppkey) // 암호화된 appkey 저장
                .appsecret(encryptedAppsecret) // 암호화된 appsecret 저장
                .account(request.getAccount())
                .build();

        return UserDTO.UserResponse.fromEntity(userRepository.save(user));
    }

    // --- 2. Read User (사용자 조회) ---
    @Transactional(readOnly = true)
    public UserDTO.UserResponse readUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "해당 ID의 사용자를 찾을 수 없습니다."));
        return UserDTO.UserResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "해당 이메일의 사용자를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "해당 ID의 사용자를 찾을 수 없습니다."));
    }

    // --- 3. Update User (사용자 정보 수정) ---
    @Transactional
    public UserDTO.UserResponse updateUser(Long userId, UserDTO.UpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "수정하려는 사용자를 찾을 수 없습니다."));

        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }
        if (request.getAppkey() != null) {
            user.setAppkey(encryptionUtil.encrypt(request.getAppkey()));
        }
        if (request.getAppsecret() != null) {
            user.setAppsecret(encryptionUtil.encrypt(request.getAppsecret()));
        }
        if(request.getPassword() != null && passwordEncoder.matches(request.getPassword(), user.getPassword())){
            String encodedPassword = passwordEncoder.encode(request.getNewPassword());
            user.setPassword(encodedPassword);
        }

        return UserDTO.UserResponse.fromEntity(user);
    }

    // --- 4. Delete User (사용자 삭제) ---
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "삭제하려는 사용자를 찾을 수 없습니다.");
        }
        userRepository.deleteById(userId);
    }

    // --- 5. Migrate User Encryption (암호화 마이그레이션) ---
    /**
     * 사용자의 암호화 데이터를 레거시 ECB에서 최신 GCM으로 마이그레이션합니다.
     * 
     * - 기존 appkey와 appsecret 복호화
     * - GCM 모드로 재암호화
     * - 데이터베이스에 저장
     * 
     * @param userId 마이그레이션할 사용자 ID
     * @throws BusinessException 사용자를 찾을 수 없거나 마이그레이션 중 오류 발생 시
     */
    @Transactional
    public void migrateUserEncryption(Long userId) {
        try {
            // 1. 사용자 조회
            User user = getUserById(userId);
            
            // 2. 기존 데이터 복호화 (GCM 우선 시도, ECB 폴백)
            String decryptedAppkey = encryptionUtil.decryptAndMigrate(user.getAppkey());
            String decryptedAppsecret = encryptionUtil.decryptAndMigrate(user.getAppsecret());
            
            // 3. GCM으로 재암호화
            user.setAppkey(encryptionUtil.encrypt(decryptedAppkey));
            user.setAppsecret(encryptionUtil.encrypt(decryptedAppsecret));
            
            // 4. 저장
            userRepository.save(user);
            
            log.info("사용자 암호화 마이그레이션 완료: userId={}, email={}", userId, user.getEmail());
        } catch (BusinessException e) {
            log.error("암호화 마이그레이션 실패 (비즈니스 예외): userId={}, errorCode={}", userId, e.getErrorCode(), e);
            throw e;
        } catch (Exception e) {
            log.error("암호화 마이그레이션 중 예상 외 오류: userId={}", userId, e);
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "암호화 마이그레이션 중 오류가 발생했습니다"
            );
        }
    }
}