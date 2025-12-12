package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.websocket.KisRealtimeWebSocketHandler;
import com.rookies4.finalProject.websocket.RealtimePriceBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeService {

    private final KisAuthService kisAuthService;
    private final UserService userService;
    private final RealtimePriceBroadcaster broadcaster;

    private static final String REAL_WS_URL = "ws://ops.koreainvestment.com:21000";
    private static final String VIRTUAL_WS_URL = "ws://ops.koreainvestment.com:31000";

    private static final String CUST_TYPE = "P"; // 개인 사용자

    public void getRealtimePrice(boolean useVirtualServer, String stockCode) {

        // 현재 로그인한 사용자 ID 조회
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        // 현재 사용자 Entity 받아오기
        User currentUser = userService.getUserById(currentUserId);

        // approval key 발급
        KisAuthTokenDTO.KisApprovalKeyResponse approvalKeyResponse =
                kisAuthService.issueApprovalKey(useVirtualServer, currentUser);
        String approvalKey = approvalKeyResponse.getApprovalKey();
        String wsUrl = useVirtualServer ? VIRTUAL_WS_URL : REAL_WS_URL;

        // WebSocket 연결
        WebSocketClient client = new StandardWebSocketClient();
        KisRealtimeWebSocketHandler handler =
                new KisRealtimeWebSocketHandler(stockCode, approvalKey, CUST_TYPE, broadcaster);

        // WebSocket 연결 시도 (Handshake)
        client.execute(handler, wsUrl)
                .whenComplete((session, throwable) -> {
                    if (throwable != null) {
                        log.error("[KIS] 연결 실패. stockCode={}, msg={}",
                                stockCode, throwable.getMessage(), throwable);
                        return;
                    }
                    log.info("[KIS] 연결 성공. stockCode={}", stockCode);
                });
    }
}
