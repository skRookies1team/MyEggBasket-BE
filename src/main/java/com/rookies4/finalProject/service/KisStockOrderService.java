package com.rookies4.finalProject.service;

import com.rookies4.finalProject.config.KisApiConfig;
import com.rookies4.finalProject.domain.entity.KisAuthToken;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisStockOrderDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.KisAuthService;
import com.rookies4.finalProject.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class KisStockOrderService {
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final KisAuthService kisAuthService;

    public KisStockOrderDTO.KisStockOrderResponse orderStock(boolean useVirtualServer, User user, String orderId){
        String path = "/uapi/domestic-stock/v1/trading/order-cash";

        // 사용자 검증
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
        }

        URI uri = KisApiConfig.uri(useVirtualServer,path);
        KisAuthToken kisAuthToken = kisAuthService.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,"token이 존재하지 않습니다."));

        String decodedAppkey = KisApiConfig.decodeBase64(user.getAppkey());
        String decodedAppsecret = KisApiConfig.decodeBase64(user.getAppsecret());
        String authorization = kisAuthToken.getTokenType() + kisAuthToken.getAccessToken();

        if(useVirtualServer){
            if(orderId.equals(("매수"))){
                String tradeId = "VTTC0012U";
            }
            else{
                String tradeId = "VTTC0011U";
            }
        }
        else{
            if(orderId.equals(("매수"))){
                String tradeId ="TTTC0012U";
            }
            else{
                String tradeId ="TTTC0011U";
            }
        }
        Map<String, String> payload = Map.of(
                "content_type", "application/json; charset=utf-8",
                "authorization",authorization,
                "appkey", decodedAppkey,
                "appsecret", decodedAppsecret,
                "tr_id",tradeId);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);
    }
}
