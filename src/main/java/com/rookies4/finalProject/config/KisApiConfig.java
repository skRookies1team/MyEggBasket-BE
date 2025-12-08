package com.rookies4.finalProject.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * KIS API 관련 설정을 관리하는 Configuration 클래스
 */
@Configuration
public class KisApiConfig {

    //실전투자 서버 Base URL
	private static final String BASE_URL = "https://openapi.koreainvestment.com:9443";

    // 모의투자 서버 Base URL
	private static final String VIRTUAL_BASE_URL = "https://openapivts.koreainvestment.com:29443";

	/**
	 * API 엔드포인트 URI를 생성합니다.
	 * @param useVirtualServer 모의투자 서버 사용 여부
	 * @param path API 경로 (예: /oauth2/tokenP, /uapi/domestic-stock/v1/trading/order-cash 등)
	 * @return 완성된 URI
	 */
    public static URI uri(boolean useVirtualServer, String path) {
        return uri(useVirtualServer, path, null); // 쿼리 파라미터 없이 호출
    }

    public static URI uri(boolean useVirtualServer, String path, Map<String, String> queryParams) {
        final String base = useVirtualServer ? VIRTUAL_BASE_URL : BASE_URL;

        // 1. 기본 URL과 Path 설정
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(base)
                .path(path.startsWith("/") ? path : "/" + path);

        // 2. 쿼리 파라미터 추가
        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(builder::queryParam);
        }

        // 3. URI 빌드 및 반환
        // .build(true)는 템플릿 변수를 확장하지 않고 인코딩하는 방식입니다.
        return builder.build(true)
                .toUri();
    }

    /**
     * Base64로 인코딩된 문자열을 디코딩합니다.
     * @param encoded 인코딩된 문자열
     * @return 디코딩된 문자열
     */

    public static String decodeBase64(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return encoded;
        }
            byte[] decodedBytes = Base64.getDecoder().decode(encoded);
            return new String(decodedBytes, StandardCharsets.UTF_8);
    }
}

