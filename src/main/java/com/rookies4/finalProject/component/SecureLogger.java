package com.rookies4.finalProject.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 민감 정보를 마스킹하여 안전하게 로깅하는 컴포넌트
 * 
 * <p>보안 관련 중요 정보(API 키, 토큰, 비밀번호 등)가 로그에 노출되지 않도록
 * 자동으로 마스킹 처리합니다.</p>
 * 
 * <p>사용 예시:</p>
 * <pre>
 * log.info("Request Body: {}", secureLogger.maskSensitive(bodyJson));
 * log.info("Response: {}", secureLogger.maskSensitiveJson(response));
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecureLogger {

    private final ObjectMapper objectMapper;

    /**
     * 마스킹 대상 필드명 목록
     * JSON 키 이름이 이 목록에 포함되면 자동으로 마스킹됩니다.
     */
    private static final List<String> SENSITIVE_FIELDS = Arrays.asList(
            "appkey",
            "appsecret",
            "approval_key",
            "password",
            "token",
            "accessToken",
            "refreshToken",
            "secret",
            "apiKey",
            "authorization",
            "bearer"
    );

    /**
     * 민감 정보 패턴 정규식
     * JSON 형식의 문자열에서 민감 정보를 찾아 마스킹합니다.
     */
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            String.format("(%s)\"\\s*:\\s*\"([^\"]+)\"",
                    String.join("|", SENSITIVE_FIELDS)),
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 마스킹 문자열
     */
    private static final String MASK = "***MASKED***";

    /**
     * JSON 문자열에서 민감 정보를 마스킹합니다.
     * 
     * @param json 원본 JSON 문자열
     * @return 민감 정보가 마스킹된 JSON 문자열
     */
    public String maskSensitive(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        try {
            // JSON 파싱이 가능한 경우, 구조적으로 마스킹
            return maskSensitiveJson(json);
        } catch (Exception e) {
            // JSON 파싱 실패 시, 정규식으로 마스킹
            log.debug("JSON 파싱 실패, 정규식으로 마스킹 진행");
            return maskSensitiveWithRegex(json);
        }
    }
    /**
     * 정규식을 사용하여 민감 정보를 마스킹합니다.
     * 
     * @param text 원본 텍스트
     * @return 민감 정보가 마스킹된 텍스트
     */
    public String maskSensitiveWithRegex(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = SENSITIVE_PATTERN.matcher(text);
        return matcher.replaceAll("$1\":\"" + MASK + "\"");
    }

    /**
     * JSON 구조를 파싱하여 민감 정보를 마스킹합니다.
     * 
     * @param json 원본 JSON 문자열
     * @return 민감 정보가 마스킹된 JSON 문자열
     * @throws JsonProcessingException JSON 처리 중 오류 발생 시
     */
    public String maskSensitiveJson(String json) throws JsonProcessingException {
        if (json == null || json.isEmpty()) {
            return json;
        }

        JsonNode jsonNode = objectMapper.readTree(json);
        maskSensitiveNode(jsonNode);
        return objectMapper.writeValueAsString(jsonNode);
    }

    /**
     * 객체를 JSON으로 변환하면서 민감 정보를 마스킹합니다.
     * 
     * @param object 변환할 객체
     * @return 민감 정보가 마스킹된 JSON 문자열
     * @throws JsonProcessingException JSON 처리 중 오류 발생 시
     */
    public String maskSensitiveJson(Object object) throws JsonProcessingException {
        if (object == null) {
            return null;
        }

        String json = objectMapper.writeValueAsString(object);
        return maskSensitiveJson(json);
    }

    /**
     * JsonNode를 재귀적으로 순회하며 민감 정보를 마스킹합니다.
     * 
     * @param node 처리할 JsonNode
     */
    private void maskSensitiveNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode fieldValue = entry.getValue();

                // 민감 필드인 경우 마스킹
                if (isSensitiveField(fieldName) && fieldValue.isTextual()) {
                    objectNode.put(fieldName, MASK);
                } else {
                    // 중첩 객체 재귀 처리
                    maskSensitiveNode(fieldValue);
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::maskSensitiveNode);
        }
    }

    /**
     * 필드명이 민감 정보인지 확인합니다.
     * 
     * @param fieldName 확인할 필드명
     * @return 민감 정보 여부
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }

        String lowerFieldName = fieldName.toLowerCase();
        return SENSITIVE_FIELDS.stream()
                .anyMatch(sensitiveField -> lowerFieldName.equals(sensitiveField.toLowerCase()));
    }

    /**
     * 안전한 로깅을 위한 헬퍼 메서드
     * 
     * @param message 로그 메시지
     * @param object 로깅할 객체
     * @return 마스킹된 문자열 (실패 시 null)
     */
    public String safeLog(String message, Object object) {
        try {
            String maskedJson = maskSensitiveJson(object);
            log.info("{}: {}", message, maskedJson);
            return maskedJson;
        } catch (Exception e) {
            log.error("로깅 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }
}
