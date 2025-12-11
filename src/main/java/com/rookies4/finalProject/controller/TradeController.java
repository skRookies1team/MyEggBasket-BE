package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.BalanceDTO;
import com.rookies4.finalProject.dto.KisStockOrderDTO;
import com.rookies4.finalProject.dto.TransactionDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.BalanceService;
import com.rookies4.finalProject.service.KisStockOrderService;
import com.rookies4.finalProject.service.TransactionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/kis/trade")
@RequiredArgsConstructor
public class TradeController {

    private static final Logger log = LoggerFactory.getLogger(TradeController.class); // Logger 인스턴스 생성

    private final KisStockOrderService kisStockOrderService;
    private final TransactionService transactionService;
    private final BalanceService balanceService;
    private final UserRepository userRepository;

    // 1. 매수/매도 주문
    @PostMapping
    public ResponseEntity<KisStockOrderDTO.KisStockOrderResponse> orderStock(
            @RequestParam(name = "virtual", defaultValue = "false") boolean useVirtualServer,
            @RequestBody KisStockOrderDTO.KisStockOrderRequest orderRequest){

        log.info("### KIS 주문 요청 (Controller) 시작 ###");
        log.info("요청 파라미터 (virtual): {}", useVirtualServer);
        // orderRequest 객체의 내용을 로깅합니다. (toString() 메서드가 잘 구현되어 있어야 유용합니다)
        log.info("요청 바디 (orderRequest): {}", orderRequest);
        log.info("------------------------------------------");

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

    // 2. 거래/주문 내역 조회 (로그인 유저 기준)
    @GetMapping("/history")
    public ResponseEntity<List<TransactionDTO.Response>> getTradeHistory(
            @RequestParam(name = "virtual", defaultValue = "false") boolean useVirtualServer,
            @RequestParam(required = false) String status) { // status 는 필수 아님 (nullable)

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        List<TransactionDTO.Response> result =
                transactionService.getUserOrders(currentUserId, status, useVirtualServer);
        return ResponseEntity.ok(result);
    }

    // 3. 잔고 조회 (보유 주식, 현금 등)
    @GetMapping("/balance")
    public ResponseEntity<BalanceDTO.Response> getAccountBalance(
            @RequestParam(name = "virtual", defaultValue = "false") boolean useVirtualServer) {

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        // KIS 잔고 조회
        BalanceDTO.Response balance = balanceService.getUserBalance(currentUserId, useVirtualServer);

        return ResponseEntity.ok(balance);
    }
}
