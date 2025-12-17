package com.rookies4.finalProject.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecureLogger 컴포넌트 단위 테스트
 */
@DisplayName("SecureLogger 테스트")
class SecureLoggerTest {

    private SecureLogger secureLogger;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        secureLogger = new SecureLogger(objectMapper);
    }

    @Test
    @DisplayName("JSON 문자열에서 appkey를 마스킹한다")
    void testMaskAppKey() {
        // given
        String json = "{\"appkey\":\"secret123\",\"data\":\"normal\"}";

        // when
        String masked = secureLogger.maskSensitive(json);

        // then
        assertThat(masked).contains("***MASKED***");
        assertThat(masked).doesNotContain("secret123");
        assertThat(masked).contains("normal");
    }

    @Test
    @DisplayName("JSON 문자열에서 appsecret을 마스킹한다")
    void testMaskAppSecret() {
        // given
        String json = "{\"appsecret\":\"topsecret456\",\"data\":\"normal\"}";

        // when
        String masked = secureLogger.maskSensitive(json);

        // then
        assertThat(masked).contains("***MASKED***");
        assertThat(masked).doesNotContain("topsecret456");
        assertThat(masked).contains("normal");
    }

    @Test
    @DisplayName("JSON 문자열에서 approval_key를 마스킹한다")
    void testMaskApprovalKey() {
        // given
        String json = "{\"approval_key\":\"approval789\",\"data\":\"normal\"}";

        // when
        String masked = secureLogger.maskSensitive(json);

        // then
        assertThat(masked).contains("***MASKED***");
        assertThat(masked).doesNotContain("approval789");
        assertThat(masked).contains("normal");
    }

    @Test
    @DisplayName("JSON 문자열에서 token을 마스킹한다")
    void testMaskToken() {
        // given
        String json = "{\"token\":\"abc123token\",\"access_token\":\"xyz456token\"}";

        // when
        String masked = secureLogger.maskSensitive(json);

        // then
        assertThat(masked).contains("***MASKED***");
        assertThat(masked).doesNotContain("abc123token");
        assertThat(masked).doesNotContain("xyz456token");
    }

    @Test
    @DisplayName("JSON 문자열에서 password를 마스킹한다")
    void testMaskPassword() {
        // given
        String json = "{\"password\":\"mypassword123\",\"data\":\"normal\"}";

        // when
        String masked = secureLogger.maskSensitive(json);

        // then
        assertThat(masked).contains("***MASKED***");
        assertThat(masked).doesNotContain("mypassword123");
        assertThat(masked).contains("normal");
    }

    @Test
    @DisplayName("여러 민감 정보를 동시에 마스킹한다")
    void testMaskMultipleSensitiveFields() {
        // given
        String json = "{\"appkey\":\"key123\",\"appsecret\":\"secret456\",\"token\":\"token789\",\"data\":\"normal\"}";

        // when
        String masked = secureLogger.maskSensitive(json);

        // then
        assertThat(masked).contains("***MASKED***");
        assertThat(masked).doesNotContain("key123");
        assertThat(masked).doesNotContain("secret456");
        assertThat(masked).doesNotContain("token789");
        assertThat(masked).contains("normal");
    }

    @Test
    @DisplayName("민감 정보가 없는 JSON은 그대로 반환한다")
    void testNoSensitiveData() {
        // given
        String json = "{\"name\":\"John\",\"age\":30,\"city\":\"Seoul\"}";

        // when
        String masked = secureLogger.maskSensitive(json);

        // then
        assertThat(masked).isEqualTo(json);
    }

    @Test
    @DisplayName("null 입력 시 null을 반환한다")
    void testNullInput() {
        // when
        String masked = secureLogger.maskSensitive(null);

        // then
        assertThat(masked).isNull();
    }

    @Test
    @DisplayName("빈 문자열 입력 시 빈 문자열을 반환한다")
    void testEmptyString() {
        // when
        String masked = secureLogger.maskSensitive("");

        // then
        assertThat(masked).isEmpty();
    }

    @Test
    @DisplayName("중첩된 JSON 객체의 민감 정보를 마스킹한다")
    void testNestedJson() throws Exception {
        // given
        String json = "{\"user\":{\"appkey\":\"key123\",\"name\":\"John\"},\"data\":\"normal\"}";

        // when
        String masked = secureLogger.maskSensitive(json);

        // then
        assertThat(masked).contains("***MASKED***");
        assertThat(masked).doesNotContain("key123");
        assertThat(masked).contains("John");
        assertThat(masked).contains("normal");
    }

    @Test
    @DisplayName("배열 내부의 민감 정보를 마스킹한다")
    void testArrayWithSensitiveData() throws Exception {
        // given
        String json = "{\"items\":[{\"appkey\":\"key1\"},{\"appkey\":\"key2\"}]}";

        // when
        String masked = secureLogger.maskSensitive(json);

        // then
        assertThat(masked).contains("***MASKED***");
        assertThat(masked).doesNotContain("key1");
        assertThat(masked).doesNotContain("key2");
    }

    @Test
    @DisplayName("대소문자 구분 없이 민감 정보를 마스킹한다")
    void testCaseInsensitive() throws Exception {
        // given
        String json = "{\"AppKey\":\"key123\",\"APPSECRET\":\"secret456\",\"Password\":\"pass789\"}";

        // when
        String masked = secureLogger.maskSensitive(json);

        // then
        assertThat(masked).contains("***MASKED***");
        assertThat(masked).doesNotContain("key123");
        assertThat(masked).doesNotContain("secret456");
        assertThat(masked).doesNotContain("pass789");
    }

    @Test
    @DisplayName("정규식 패턴으로 민감 정보를 마스킹한다")
    void testMaskSensitiveWithRegex() {
        // given
        String text = "Request: {\"appkey\":\"secret123\", \"data\":\"normal\"}";

        // when
        String masked = secureLogger.maskSensitiveWithRegex(text);

        // then
        assertThat(masked).contains("***MASKED***");
        assertThat(masked).doesNotContain("secret123");
        assertThat(masked).contains("normal");
    }

    @Test
    @DisplayName("객체를 JSON으로 변환하면서 민감 정보를 마스킹한다")
    void testMaskSensitiveJsonObject() throws Exception {
        // given
        TestDto testDto = new TestDto("key123", "secret456", "normalData");

        // when
        String masked = secureLogger.maskSensitiveJson(testDto);

        // then
        assertThat(masked).contains("***MASKED***");
        assertThat(masked).doesNotContain("key123");
        assertThat(masked).doesNotContain("secret456");
        assertThat(masked).contains("normalData");
    }

    // 테스트용 DTO
    static class TestDto {
        public String appkey;
        public String appsecret;
        public String data;

        public TestDto(String appkey, String appsecret, String data) {
            this.appkey = appkey;
            this.appsecret = appsecret;
            this.data = data;
        }
    }
}
