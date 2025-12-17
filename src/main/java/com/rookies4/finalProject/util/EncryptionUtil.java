package com.rookies4.finalProject.util;

import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256 암호화/복호화 서비스
 * - Thread-safe: 인스턴스 필드와 메서드로 구현
 * - 키 불변성: 생성자 주입으로 키 변경 방지
 * - 테스트 가능: DI를 통해 테스트 시 키 변경 가능
 */
@Slf4j
@Component
public class EncryptionUtil {

    private final SecretKeySpec keySpec;
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /**
     * 생성자를 통한 키 주입 (불변성 보장)
     * @param secretKey application.properties의 encryption.secret-key 값
     */
    public EncryptionUtil(@Value("${encryption.secret-key}") String secretKey) {
        this.keySpec = generateKey(secretKey);
    }

    /**
     * 문자열을 AES 알고리즘으로 암호화합니다.
     * @param plainText 평문
     * @return 암호화된 문자열 (Base64 인코딩)
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("암호화 실패: plainText length={}", plainText.length(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "데이터 암호화 중 오류가 발생했습니다.");
        }
    }

    /**
     * 암호화된 문자열을 AES 알고리즘으로 복호화합니다.
     * @param encryptedText 암호화된 문자열 (Base64 인코딩)
     * @return 복호화된 평문
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
            byte[] decrypted = cipher.doFinal(decodedBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("복호화 실패: encryptedText length={}", encryptedText.length(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "데이터 복호화 중 오류가 발생했습니다.");
        }
    }

    /**
     * SECRET_KEY를 32바이트(256비트)로 정규화하여 SecretKeySpec 생성
     * - 32자 미만: SHA-256 해시로 32바이트 생성
     * - 32자 초과: 앞 32바이트만 사용
     */
    private SecretKeySpec generateKey(String secretKey) {
        try {
            byte[] key = secretKey.getBytes(StandardCharsets.UTF_8);

            if (key.length == 32) {
                // 정확히 32바이트면 그대로 사용
                return new SecretKeySpec(key, ALGORITHM);
            } else if (key.length < 32) {
                // 32바이트 미만이면 SHA-256 해시로 32바이트 생성
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                key = sha.digest(key);
                return new SecretKeySpec(key, ALGORITHM);
            } else {
                // 32바이트 초과면 앞 32바이트만 사용
                key = Arrays.copyOf(key, 32);
                return new SecretKeySpec(key, ALGORITHM);
            }
        } catch (Exception e) {
            log.error("암호화 키 생성 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "암호화 키 생성 중 오류가 발생했습니다.");
        }
    }
}