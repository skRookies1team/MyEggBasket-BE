package com.rookies4.finalProject.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * KIS API 관련 설정을 관리하는 Configuration 클래스
 */
@Configuration
public class KisApiConfig {

    //실전투자 서버 Base URL
	public static final String BASE_URL = "https://openapi.koreainvestment.com:9443";

    // 모의투자 서버 Base URL
	private static final String VIRTUAL_BASE_URL = "https://openapivts.koreainvestment.com:29443";

    /**
     * 토큰 발급 URL을 반환합니다.
     */
    public static String tokenUrl(boolean useVirtualServer) {
        final String base = useVirtualServer ? VIRTUAL_BASE_URL : BASE_URL;
        return base + "/oauth2/tokenP";
    }

    /**
     * 웹소켓 접속키 발급 URL을 반환합니다.
     */
    public static String approvalUrl(boolean useVirtualServer) {
        final String base = useVirtualServer ? VIRTUAL_BASE_URL : BASE_URL;
        return base + "/oauth2/Approval";
    }

	/**
	 * API 엔드포인트 URI를 생성합니다.
	 * @param useVirtualServer 모의투자 서버 사용 여부
	 * @param path API 경로 (예: /uapi/domestic-stock/v1/trading/order-cash 등)
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
        return builder.build(true)
                .toUri();
    }
}