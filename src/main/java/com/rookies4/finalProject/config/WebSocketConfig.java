package com.rookies4.finalProject.config;

import com.rookies4.finalProject.websocket.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@RequiredArgsConstructor
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1. 일반적인 STOMP 설정
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // 모든 출처 허용 (보안상 주의, 개발용)
                // .setAllowedOrigins("http://localhost:5173") // 또는 특정 프론트 주소만 허용
                .withSockJS(); // SockJS 사용 시 (클라이언트 코드에 따라 다름)

        // 2. 만약 SockJS 없이 순수 STOMP만 쓴다면 .withSockJS()를 빼야 할 수도 있습니다.
        // registry.addEndpoint("/ws")
        //        .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }
}