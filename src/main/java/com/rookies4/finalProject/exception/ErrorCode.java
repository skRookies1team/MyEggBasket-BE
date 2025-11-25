package com.rookies4.finalProject.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor // ⭐️ 모든 필드를 포함하는 생성자를 자동으로 생성
public enum ErrorCode {

    // ----------------------------------------------------
    // 인증/인가 관련 (40X)
    // ----------------------------------------------------
    AUTH_INVALID_CREDENTIALS("AUTH_001", "이메일 또는 비밀번호가 올바르지 않습니다", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRED("AUTH_002", "토큰이 만료되었습니다", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID("AUTH_003", "유효하지 않은 토큰입니다", HttpStatus.UNAUTHORIZED),
    AUTH_ACCESS_DENIED("AUTH_004", "접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_LOCKED("AUTH_005", "계정이 잠겨있습니다", HttpStatus.FORBIDDEN),
    AUTH_INVALID_PASSWORD("AUTH_006", "비밀번호가 올바르지 않습니다", HttpStatus.UNAUTHORIZED),

    // ----------------------------------------------------
    // 검증 관련 (40X)
    // ----------------------------------------------------
    VALIDATION_ERROR("VALID_001", "입력값이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    REQUIRED_FIELD_MISSING("VALID_002", "필수 항목이 누락되었습니다", HttpStatus.BAD_REQUEST),
    INVALID_FORMAT("VALID_003", "형식이 올바르지 않습니다", HttpStatus.BAD_REQUEST),

    // ----------------------------------------------------
    // 🔹 비즈니스 로직 관련 (403 / 400)
    // ----------------------------------------------------
    BUSINESS_RULE_VIOLATION("BIZ_001", "비즈니스 규칙 위반입니다.", HttpStatus.BAD_REQUEST),
    OPERATION_NOT_ALLOWED("BIZ_002", "허용되지 않은 작업입니다.", HttpStatus.FORBIDDEN),

    // ----------------------------------------------------
    // 🔹 사용자 관련
    // ----------------------------------------------------
    USER_NOT_FOUND("USER_001", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    USER_ID_DUPLICATE("USER_002", "중복된 아이디입니다.", HttpStatus.CONFLICT),
    USER_NAME_DUPLICATE("USER_003", "중복된 이름입니다.", HttpStatus.CONFLICT),


    //-----------------------------------------------------
    // 주식 관련
    //-----------------------------------------------------
    STOCK_TICKER_DUPLICATE("STOCK_001", "중복된 종목입니다.", HttpStatus.CONFLICT),
    TICKER_NOT_FOUND("STOCK_002","해당 종목을 찾을 수 없습니다.",HttpStatus.CONFLICT),

    // 🔹 서버 오류 (500)
    // ----------------------------------------------------
    INTERNAL_SERVER_ERROR("SERVER_001", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR("SERVER_002", "데이터베이스 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    EXTERNAL_API_ERROR("SERVER_003", "외부 API 호출 중 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}