package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisStockOrderDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.KisStockOrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/kis/trade")
@RequiredArgsConstructor
public class KisStockOrderController {

    private static final Logger log = LoggerFactory.getLogger(KisStockOrderController.class); // Logger 인스턴스 생성

    private final KisStockOrderService kisStockOrderService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<KisStockOrderDTO.KisStockOrderResponse> orderStock(
            @RequestParam(name = "virtual", defaultValue = "false") boolean useVirtualServer,
            @RequestBody KisStockOrderDTO.KisStockOrderRequest orderRequest){

        // 💡 추가된 로깅 부분 시작
        log.info("### KIS 주문 요청 (Controller) 시작 ###");
        log.info("요청 파라미터 (virtual): {}", useVirtualServer);
        // orderRequest 객체의 내용을 로깅합니다. (toString() 메서드가 잘 구현되어 있어야 유용합니다)
        log.info("요청 바디 (orderRequest): {}", orderRequest);
        log.info("------------------------------------------");
        // 💡 추가된 로깅 부분 끝

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }
        // 사용자 조회
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "로그인한 사용자를 찾을 수 없습니다."));


        return ResponseEntity.ok(kisStockOrderService.orderStock(useVirtualServer, user, orderRequest));
    }
}
