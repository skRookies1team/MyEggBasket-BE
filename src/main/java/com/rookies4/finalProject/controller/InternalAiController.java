package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.KisBalanceDTO;
import com.rookies4.finalProject.dto.KisStockOrderDTO; // DTO 전체 import
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.service.KisBalanceService;
import com.rookies4.finalProject.service.KisStockOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalAiController {

    private final UserRepository userRepository;
    private final KisBalanceService kisBalanceService;
    private final KisStockOrderService kisStockOrderService;

    @Value("${ai.secret-key:my-secret-ai-key}")
    private String aiSecretKey;

    private void validateSecret(String requestKey) {
        if (!aiSecretKey.equals(requestKey)) {
            throw new RuntimeException("Invalid AI Secret Key");
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<Long>> getActiveUsers(@RequestHeader("X-AI-SECRET") String secret) {
        validateSecret(secret);
        List<Long> userIds = userRepository.findAll().stream()
                .map(User::getId)
                .collect(Collectors.toList());
        return ResponseEntity.ok(userIds);
    }

    @GetMapping("/balance/{userId}")
    public ResponseEntity<KisBalanceDTO> getUserBalance(
            @RequestHeader("X-AI-SECRET") String secret,
            @PathVariable Long userId) {
        validateSecret(secret);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // accessToken은 null, useVirtual은 true로 설정 (상황에 따라 false 변경)
        return ResponseEntity.ok(kisBalanceService.getBalanceFromKis(user, null, true));
    }

    @PostMapping("/trade/{userId}")
    public ResponseEntity<?> orderForUser(
            @RequestHeader("X-AI-SECRET") String secret,
            @PathVariable Long userId,
            // [수정 1] RequestBody 타입을 Inner Class로 명시적으로 지정
            @RequestBody KisStockOrderDTO.KisStockOrderRequest requestDto) {

        validateSecret(secret);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // [수정 2] 서비스 호출 결과를 ResponseEntity.ok()로 감싸기
        // requestDto를 바로 전달 (이미 타입이 일치함)
        return ResponseEntity.ok(kisStockOrderService.orderStock(true, user, requestDto));
    }
}