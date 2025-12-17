package com.rookies4.finalProject.util;

import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM 암호화/복호화 서비스
 * - Thread-safe: 인스턴스 필드와 메서드로 구현
 * - 키 불변성: 생성자 주입으로 키 변경 방지
 * - 테스트 가능: DI를 통해 테스트 시 키 변경 가능
 * - 보안: GCM 모드로 암호화 및 인증 태그 검증
 * - IV(Initialization Vector): 각 암호화마다 랜덤 IV 생성
 */
@Slf4j
@Component
public class EncryptionUtil {

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom;
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits authentication tag

    /**
     * 생성자를 통한 키 주입 (불변성 보장)
     * @param secretKey application.properties의 encryption.secret-key 값
     */
    public EncryptionUtil(@Value("${encryption.secret-key}") String secretKey) {
        this.keySpec = generateKey(secretKey);
        this.secureRandom = new SecureRandom();
    }

    /**
     * 문자열을 AES-GCM 알고리즘으로 암호화합니다.
     * IV(Initialization Vector)를 랜덤 생성하여 암호문 앞에 포함합니다.
     * 
     * @param plainText 평문
     * @return IV + 암호문을 Base64 인코딩한 문자열
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        try {
            // 랜덤 IV 생성
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // GCM 파라미터 설정
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            
            // 암호화
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // IV + 암호문을 결합
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encrypted.length);
            byteBuffer.put(iv);
            byteBuffer.put(encrypted);
            
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("암호화 실패: plainText length={}", plainText.length(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "데이터 암호화 중 오류가 발생했습니다.");
        }
    }

    /**
     * 암호화된 문자열을 복호화합니다.
     * - GCM 모드로 암호화된 데이터 (IV 포함) 우선 시도
     * - 실패 시 구버전 ECB 모드 복호화 시도 (하위 호환성)
     * 
     * @param encryptedText 암호화된 문자열 (Base64 인코딩)
     * @return 복호화된 평문
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return null;
        }
        
        try {
            // GCM 모드 복호화 시도
            return decryptGCM(encryptedText);
        } catch (Exception gcmException) {
            // GCM 복호화 실패 시 레거시 ECB 모드로 시도
            log.debug("GCM 복호화 실패, ECB 모드로 재시도: {}", gcmException.getMessage());
            try {
                String decrypted = decryptLegacyECB(encryptedText);
                log.warn("레거시 ECB 암호화 데이터 감지. 보안을 위해 재암호화를 권장합니다.");
                return decrypted;
            } catch (Exception ecbException) {
                log.error("복호화 실패 (GCM 및 ECB 모두 실패): encryptedText length={}", encryptedText.length());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "데이터 복호화 중 오류가 발생했습니다.");
            }
        }
    }

    /**
     * GCM 모드로 암호화된 데이터를 복호화합니다.
     * 
     * @param encryptedText IV + 암호문을 Base64 인코딩한 문자열
     * @return 복호화된 평문
     * @throws Exception 복호화 실패 시
     */
    private String decryptGCM(String encryptedText) throws Exception {
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
        
        // IV와 암호문 분리
        ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
        byte[] iv = new byte[GCM_IV_LENGTH];
        byteBuffer.get(iv);
        byte[] encrypted = new byte[byteBuffer.remaining()];
        byteBuffer.get(encrypted);
        
        // GCM 파라미터 설정
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        // 복호화
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
        byte[] decrypted = cipher.doFinal(encrypted);
        
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * 레거시 ECB 모드로 암호화된 데이터를 복호화합니다.
     * (하위 호환성을 위한 메서드 - 새로운 암호화에는 사용하지 않음)
     * 
     * @param encryptedText ECB로 암호화된 Base64 인코딩 문자열
     * @return 복호화된 평문
     * @throws Exception 복호화 실패 시
     */
    private String decryptLegacyECB(String encryptedText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decrypted = cipher.doFinal(decodedBytes);
        return new String(decrypted, StandardCharsets.UTF_8);
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