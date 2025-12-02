package com.rookies4.finalProject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration // 이 클래스가 Spring 설정 클래스임을 명시
public class RestTemplateConfig {

    /**
     * RestTemplate 빈을 생성하고 등록합니다.
     * RestTemplate는 HTTP 요청을 동기적으로 처리하는 Spring의 기본 클라이언트입니다.
     */
    @Bean
    public RestTemplate restTemplate() {
        // 기본 RestTemplate 인스턴스를 생성하여 빈으로 등록합니다.
        // 필요하다면 여기에 커넥션 풀, 타임아웃, 인터셉터 등의 설정을 추가할 수 있습니다.
        return new RestTemplate();
    }
}