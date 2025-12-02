package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisStockOrderDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.KisStockOrderService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/kis/trade")
@RequiredArgsConstructor
public class KisStockOrderController {
    private final KisStockOrderService kisStockOrderService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<KisStockOrderDTO.KisStockOrderResponse> orderStock(
            @RequestParam(name = "virtual", defaultValue = "false") boolean useVirtualServer,
            @RequestBody KisStockOrderDTO.KisStockOrderRequest orderRequest){

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
