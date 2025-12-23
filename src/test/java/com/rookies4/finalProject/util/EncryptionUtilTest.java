package com.rookies4.finalProject.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EncryptionUtil 단위 테스트
 * - 인스턴스 기반 암호화/복호화 테스트
 * - 멀티 인스턴스 테스트 (서로 다른 키로 암호화된 데이터 처리)
 */
@ExtendWith(MockitoExtension.class)
class EncryptionUtilTest {

    private EncryptionUtil encryptionUtil;
    private static final String TEST_SECRET_KEY = "test-secret-key-for-encryption";

    @BeforeEach
    void setUp() {
        // 각 테스트마다 새로운 인스턴스 생성
        encryptionUtil = new EncryptionUtil(TEST_SECRET_KEY);
    }

    @Test
    @DisplayName("평문을 암호화하고 복호화하면 원래 값으로 복원된다")
    void encrypt_decrypt_success() {
        // given
        String plainText = "sensitive-data-12345";

        // when
        String encrypted = encryptionUtil.encrypt(plainText);
        String decrypted = encryptionUtil.decrypt(encrypted);

        // then
        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("null 입력 시 null 반환")
    void encrypt_null_returns_null() {
        // when
        String encrypted = encryptionUtil.encrypt(null);
        String decrypted = encryptionUtil.decrypt(null);

        // then
        assertThat(encrypted).isNull();
        assertThat(decrypted).isNull();
    }

    @Test
    @DisplayName("빈 문자열 입력 시 null 반환")
    void encrypt_empty_returns_null() {
        // when
        String encrypted = encryptionUtil.encrypt("");
        String decrypted = encryptionUtil.decrypt("");

        // then
        assertThat(encrypted).isNull();
        assertThat(decrypted).isNull();
    }

    @Test
    @DisplayName("동일한 평문을 여러 번 암호화하면 서로 다른 암호문 생성 (GCM 모드 - 랜덤 IV)")
    void encrypt_same_plaintext_produces_different_ciphertexts() {
        // given
        String plainText = "test-data";

        // when
        String encrypted1 = encryptionUtil.encrypt(plainText);
        String encrypted2 = encryptionUtil.encrypt(plainText);

        // then
        // 랜덤 IV로 인해 암호문이 달라야 함
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        
        // 하지만 둘 다 정상적으로 복호화되어 원본과 일치해야 함
        assertThat(encryptionUtil.decrypt(encrypted1)).isEqualTo(plainText);
        assertThat(encryptionUtil.decrypt(encrypted2)).isEqualTo(plainText);
    }

    @Test
    @DisplayName("한글 데이터 암호화/복호화")
    void encrypt_korean_text() {
        // given
        String koreanText = "한국투자증권 API 키 테스트";

        // when
        String encrypted = encryptionUtil.encrypt(koreanText);
        String decrypted = encryptionUtil.decrypt(encrypted);

        // then
        assertThat(decrypted).isEqualTo(koreanText);
    }

    @Test
    @DisplayName("긴 문자열 암호화/복호화")
    void encrypt_long_text() {
        // given
        String longText = "a".repeat(1000);

        // when
        String encrypted = encryptionUtil.encrypt(longText);
        String decrypted = encryptionUtil.decrypt(encrypted);

        // then
        assertThat(decrypted).isEqualTo(longText);
    }

    @Test
    @DisplayName("서로 다른 키로 생성된 인스턴스는 독립적으로 동작한다")
    void different_instances_with_different_keys() {
        // given
        EncryptionUtil util1 = new EncryptionUtil("key1");
        EncryptionUtil util2 = new EncryptionUtil("key2");
        String plainText = "test-data";

        // when
        String encrypted1 = util1.encrypt(plainText);
        String encrypted2 = util2.encrypt(plainText);

        // then
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(util1.decrypt(encrypted1)).isEqualTo(plainText);
        assertThat(util2.decrypt(encrypted2)).isEqualTo(plainText);
    }

    @Test
    @DisplayName("특수문자 포함 데이터 암호화/복호화")
    void encrypt_special_characters() {
        // given
        String specialText = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

        // when
        String encrypted = encryptionUtil.encrypt(specialText);
        String decrypted = encryptionUtil.decrypt(encrypted);

        // then
        assertThat(decrypted).isEqualTo(specialText);
    }

    @Test
    @DisplayName("32바이트 미만 키로 생성된 인스턴스도 정상 동작")
    void short_key_works() {
        // given
        EncryptionUtil shortKeyUtil = new EncryptionUtil("short");
        String plainText = "test";

        // when
        String encrypted = shortKeyUtil.encrypt(plainText);
        String decrypted = shortKeyUtil.decrypt(encrypted);

        // then
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("32바이트 초과 키로 생성된 인스턴스도 정상 동작")
    void long_key_works() {
        // given
        String longKey = "this-is-a-very-long-key-that-exceeds-32-bytes-in-length";
        EncryptionUtil longKeyUtil = new EncryptionUtil(longKey);
        String plainText = "test";

        // when
        String encrypted = longKeyUtil.encrypt(plainText);
        String decrypted = longKeyUtil.decrypt(encrypted);

        // then
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("레거시 ECB 암호화 데이터 복호화 (하위 호환성)")
    void decrypt_legacy_ecb_data() throws Exception {
        // given - ECB 모드로 암호화된 데이터 시뮬레이션
        String plainText = "legacy-test-data";
        
        // ECB 암호화 수행 (레거시 방식)
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
        String secretKey = "test-secret-key-for-encryption";
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(key, "AES");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String legacyEncryptedText = java.util.Base64.getEncoder().encodeToString(encrypted);
        
        // when - 새로운 EncryptionUtil로 레거시 데이터 복호화
        String decrypted = encryptionUtil.decrypt(legacyEncryptedText);
        
        // then - 정상적으로 복호화되어야 함
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("잘못된 암호문 복호화 시 예외 발생")
    void decrypt_invalid_ciphertext_throws_exception() {
        // given
        String invalidCiphertext = "invalid-base64-string!@#";

        // when & then
        assertThatThrownBy(() -> encryptionUtil.decrypt(invalidCiphertext))
                .hasMessageContaining("서버 내부 오류가 발생했습니다.");
    }
}
