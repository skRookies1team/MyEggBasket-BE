package com.rookies4.finalProject.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
public class Base64Util {

    /**
     * Base64로 문자열을 인코딩합니다.
     * @param plainText 인코딩할 문자열
     * @return Base64로 인코딩된 문자열 (입력이 null/empty면 그대로 반환)
     */
    public static String encode(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            return Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Base64 인코딩 실패, 원본 값 사용: {}", e.getMessage());
            return plainText;
        }
    }

    /**
     * Base64로 인코딩된 문자열을 디코딩합니다.
     * @param encoded 인코딩된 문자열
     * @return 디코딩된 문자열 (디코딩 실패 시 원본 반환)
     */
    public static String decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return encoded;
        }
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encoded);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("Base64 디코딩 실패, 원본 값 사용: {}", e.getMessage());
            return encoded;
        }
    }
}