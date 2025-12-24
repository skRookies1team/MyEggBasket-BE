package com.rookies4.finalProject.controller;

import com.rookies4.finalProject.dto.AIRecommendationDTO;
import com.rookies4.finalProject.service.AIRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AIRecommendationController {

    private final AIRecommendationService aiRecommendationService;

    @PostMapping("/ai-recommendations")
    public ResponseEntity<AIRecommendationDTO.RecommendationResponse> createRecommendation(
            @Valid @RequestBody AIRecommendationDTO.RecommendationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(aiRecommendationService.createRecommendation(request));
    }

    @GetMapping("/portfolios/{portfolioId}/ai-recommendations")
    public ResponseEntity<List<AIRecommendationDTO.RecommendationResponse>> getRecommendations(
            @PathVariable Long portfolioId) {
        return ResponseEntity.ok(aiRecommendationService.getRecentRecommendations(portfolioId));
    }
}
