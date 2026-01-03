package com.rookies4.finalProject.websocket;

import com.rookies4.finalProject.security.JwtTokenProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    public static final String AUTHENTICATED = "AUTHENTICATED";
    public static final String USER_ID = "USER_ID";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.COMMIT.equals(accessor.getCommand())) {
            String bearerToken = accessor.getFirstNativeHeader("Authorization");
            String token = jwtTokenProvider.resolveToken(bearerToken);

            if (token == null || !jwtTokenProvider.validateToken(token)) {
                log.warn("[WS] STOMP CONNECT rejected: invalid JWT");
                throw new IllegalArgumentException("Invalid JWT token");
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(token);

            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            sessionAttributes.put(AUTHENTICATED, true);
            sessionAttributes.put(USER_ID, userId);

            log.info("[WS] STOMP CONNECT authenticated userId={}", userId);
        }

        return message;
    }
}
