package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.AIRecommendation;
import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.dto.AIRecommendationDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.AIRecommendationRepository;
import com.rookies4.finalProject.repository.PortfolioRepository;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AIRecommendationService {

    private final AIRecommendationRepository aiRecommendationRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;

    public AIRecommendationDTO.RecommendationResponse createRecommendation(AIRecommendationDTO.RecommendationCreateRequest request) {
        Long currentUserId = requireCurrentUser();

        Portfolio portfolio = portfolioRepository.findById(request.getPortfolioId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));
        validateOwnership(portfolio, currentUserId);

        Stock stock = stockRepository.findById(request.getStockCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND));

        AIRecommendation recommendation = AIRecommendation.builder()
                .portfolio(portfolio)
                .stock(stock)
                .aiScore(request.getAiScore())
                .actionType(request.getActionType())
                .currentHolding(request.getCurrentHolding())
                .targetHolding(request.getTargetHolding())
                .targetHoldingPercentage(request.getTargetHoldingPercentage())
                .adjustmentAmount(request.getAdjustmentAmount())
                .reasonSummary(request.getReasonSummary())
                .riskWarning(request.getRiskWarning())
                .build();

        return AIRecommendationDTO.RecommendationResponse.fromEntity(aiRecommendationRepository.save(recommendation));
    }

    @Transactional(readOnly = true)
    public List<AIRecommendationDTO.RecommendationResponse> getRecentRecommendations(Long portfolioId) {
        Long currentUserId = requireCurrentUser();

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));
        validateOwnership(portfolio, currentUserId);

        Pageable pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt"));
        return aiRecommendationRepository.findByPortfolio(portfolio, pageable)
                .stream()
                .map(AIRecommendationDTO.RecommendationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    private Long requireCurrentUser() {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }
        return currentUserId;
    }

    private void validateOwnership(Portfolio portfolio, Long userId) {
        if (portfolio.getUser() == null || !portfolio.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "해당 포트폴리오에 대한 접근 권한이 없습니다.");
        }
    }
}
