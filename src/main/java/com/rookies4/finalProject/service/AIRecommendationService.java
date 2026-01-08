package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.AIRecommendation;
import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User; // [추가]
import com.rookies4.finalProject.domain.enums.RecommendationAction;
import com.rookies4.finalProject.dto.AIRecommendationDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.AIRecommendationRepository;
import com.rookies4.finalProject.repository.PortfolioRepository;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.repository.UserRepository; // [추가]
import com.rookies4.finalProject.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // [추가]
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal; // [추가]
import java.util.List;
import java.util.stream.Collectors;

@Slf4j // [추가]
@Service
@RequiredArgsConstructor
@Transactional
public class AIRecommendationService {

    private final AIRecommendationRepository aiRecommendationRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository; // [추가] 사용자 조회를 위해 주입

    // 기존 메서드 (웹 프론트엔드용)
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

    // [추가] 내부 AI 시스템용 (Python 봇 연동)
    public void saveRecommendationForUser(Long userId, String stockCode, String actionStr, String reason, Float score) {
        // 1. 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 포트폴리오 조회 (없으면 기본 포트폴리오 생성)
        // 사용자의 포트폴리오 중 하나를 가져오거나 없으면 생성하는 로직
        List<Portfolio> portfolios = portfolioRepository.findByUser(user);
        Portfolio portfolio;

        if (portfolios.isEmpty()) {
            portfolio = Portfolio.builder()
                    .user(user)
                    .name("My AI Portfolio") // 기본 이름
                    .build();
            portfolio = portfolioRepository.save(portfolio);
            log.info("[AI] User {}의 기본 포트폴리오 생성 완료", userId);
        } else {
            // 편의상 첫 번째 포트폴리오 사용 (추후 로직 고도화 가능)
            portfolio = portfolios.get(0);
        }

        // 3. 종목 조회
        Stock stock = stockRepository.findById(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND, "Stock not found: " + stockCode));

        // 4. Action Enum 변환
        RecommendationAction actionType;
        try {
            actionType = RecommendationAction.valueOf(actionStr.toUpperCase());
        } catch (Exception e) {
            actionType = RecommendationAction.HOLD; // 기본값
        }

        // 5. 엔티티 생성 및 저장
        // Python 봇은 상세 금액 계산(currentHolding 등)을 보내지 않을 수도 있으므로 기본값(0) 처리
        AIRecommendation recommendation = AIRecommendation.builder()
                .portfolio(portfolio)
                .stock(stock)
                .aiScore(score != null ? score : 50.0f)
                .actionType(actionType)
                .reasonSummary(reason)
                .riskWarning("AI System Auto-generated")
                .currentHolding(BigDecimal.ZERO)
                .targetHolding(BigDecimal.ZERO)
                .targetHoldingPercentage(0.0f)
                .adjustmentAmount(BigDecimal.ZERO)
                .build();

        aiRecommendationRepository.save(recommendation);
        log.info("[AI] 추천 저장 완료 - User: {}, Stock: {}, Action: {}", userId, stockCode, actionStr);
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
    public AIRecommendationDTO.RecommendationResponse createRecommendationByAI(AIRecommendationDTO.RecommendationCreateRequest request) {
        Portfolio portfolio;

        // 1. Portfolio 결정 로직 (userId 우선)
        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            // 유저의 포트폴리오 목록 조회
            List<Portfolio> portfolios = portfolioRepository.findByUser(user);

            if (portfolios.isEmpty()) {
                // 포트폴리오가 없으면 자동 생성
                portfolio = Portfolio.builder()
                        .user(user)
                        .name("My AI Portfolio")
                        .build();
                portfolio = portfolioRepository.save(portfolio);
                log.info("[AI Service] User {}의 새 포트폴리오 생성", request.getUserId());
            } else {
                // 기존 포트폴리오 사용 (첫 번째 것)
                portfolio = portfolios.get(0);
            }
        } else if (request.getPortfolioId() != null) {
            // userId가 없고 portfolioId만 있는 경우
            portfolio = portfolioRepository.findById(request.getPortfolioId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));
        } else {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "userId 또는 portfolioId 중 하나는 필수입니다.");
        }

        // 2. 주식 종목 조회
        Stock stock = stockRepository.findById(request.getStockCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND));

        // 3. 엔티티 생성 및 저장
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
}