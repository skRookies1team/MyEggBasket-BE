package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.UserDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}