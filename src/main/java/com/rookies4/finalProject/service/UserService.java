package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.UserDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.usertype.UserType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Create user
    public UserDTO.UserResponse createUser(UserDTO.SignUpRequest request) {

        if(request.getPassword().equals(request.getConfirmPassword()))
        {
            // 비밀번호 암호화
            String encodedPassword = passwordEncoder.encode(request.getPassword());

            //이메일 중복
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException(ErrorCode.USER_ID_DUPLICATE);
            }

            User user = User.builder()
                    .email(request.getEmail())
                    .password(encodedPassword)
                    .username(request.getUsername())
                    .appkey(request.getAppkey())
                    .appsecret(request.getAppsecret())
                    .build();
            return UserDTO.UserResponse.fromEntity(userRepository.save(user));
        }
        else
            throw new BusinessException(ErrorCode.USER_ID_DUPLICATE);
    }
}
