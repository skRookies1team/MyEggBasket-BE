package com.rookies4.finalProject.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    /**
     * 현재 인증된 사용자의 ID를 가져옵니다.
     * @return 사용자 ID, 인증되지 않은 경우 null
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal) {
            CustomUserPrincipal userPrincipal = (CustomUserPrincipal) authentication.getPrincipal();
            return userPrincipal.getId();
        }
        return null;
    }

    /**
     * 현재 인증된 사용자의 이메일을 가져옵니다.
     * @return 사용자 이메일, 인증되지 않은 경우 null
     */
    public static String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal) {
            CustomUserPrincipal userPrincipal = (CustomUserPrincipal) authentication.getPrincipal();
            return userPrincipal.getEmail();
        }
        return null;
    }

    /**
     * 현재 인증된 사용자 정보를 가져옵니다.
     * @return CustomUserPrincipal, 인증되지 않은 경우 null
     */
    public static CustomUserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal) {
            return (CustomUserPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}

