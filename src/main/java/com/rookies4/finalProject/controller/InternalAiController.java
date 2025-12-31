package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.KisBalanceDTO;
import com.rookies4.finalProject.dto.KisStockOrderDTO;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.service.AIRecommendationService; // [추가]
import com.rookies4.finalProject.service.KisBalanceService;
import com.rookies4.finalProject.service.KisStockOrderService;
import lombok.Data; // [추가]
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
    private final AIRecommendationService aiRecommendationService; // [추가] 서비스 주입

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

        return ResponseEntity.ok(kisBalanceService.getBalanceFromKis(user, null, true));
    }

    @PostMapping("/trade/{userId}")
    public ResponseEntity<?> orderForUser(
            @RequestHeader("X-AI-SECRET") String secret,
            @PathVariable Long userId,
            @RequestBody KisStockOrderDTO.KisStockOrderRequest requestDto) {

        validateSecret(secret);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(kisStockOrderService.orderStock(true, user, requestDto));
    }

    // [추가] AI 조언 수신 엔드포인트
    @PostMapping("/ai/recommendation")
    public ResponseEntity<?> receiveRecommendation(
            @RequestHeader("X-AI-SECRET") String secret,
            @RequestBody InternalRecommendationRequest request) {

        validateSecret(secret);

        // 서비스 호출하여 저장
        aiRecommendationService.saveRecommendationForUser(
                request.getUserId(),
                request.getStockCode(),
                request.getType(),
                request.getReason(),
                request.getScore()
        );

        return ResponseEntity.ok().body("Recommendation saved successfully");
    }

    // [추가] Python Payload 매핑용 DTO
    @Data
    public static class InternalRecommendationRequest {
        private Long userId;
        private String stockCode;
        private String type; // "BUY", "SELL", "HOLD"
        private String reason;
        private Float score;
    }
}