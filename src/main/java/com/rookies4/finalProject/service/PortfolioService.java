package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Holding;
import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.PortfolioDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.PortfolioRepository;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;

    //1. Portfolio 생성
    public PortfolioDTO.PortfolioResponse createPortfolio(PortfolioDTO.PortfolioRequest request){
        if (portfolioRepository.existsByName(request.getName())){
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "동일한 이름의 포트폴리오가 이미 존재합니다.");
        }

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "로그인한 사용자를 찾을 수 없습니다."));

        Portfolio portfolio = Portfolio.builder()
                .user(user)
                .name(request.getName())
                .totalAsset(defaultZero(request.getTotalAsset()))
                .cashBalance(defaultZero(request.getCashBalance()))
                .riskLevel(request.getRiskLevel())
                .build();

        return PortfolioDTO.PortfolioResponse.fromEntity(portfolioRepository.save(portfolio));
    }
    //2. Portfolio 전체 조회
    @Transactional(readOnly = true)
    public List<PortfolioDTO.PortfolioResponse> readPortfolios() {
        return portfolioRepository.findAll()
                .stream()
                .map(PortfolioDTO.PortfolioResponse::fromEntity)
                .collect(Collectors.toList());
    }

    //3. Portfolio 상세 조회
    @Transactional(readOnly = true)
    public PortfolioDTO.PortfolioResponse readPortfolio(Long portfolioId){
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOIO_NOT_FOUND));
        return PortfolioDTO.PortfolioResponse.fromEntity(portfolio);
    }
    //4. Portfolio 수정
    public PortfolioDTO.PortfolioResponse updatePortfolio(Long portfolioId, PortfolioDTO.PortfolioRequest updateRequest){
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOIO_NOT_FOUND));

        if (updateRequest.getName() != null && !updateRequest.getName().isBlank()) {
            if (!updateRequest.getName().equals(portfolio.getName()) && portfolioRepository.existsByName(updateRequest.getName())) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "동일한 이름의 포트폴리오가 이미 존재합니다.");
            }
            portfolio.setName(updateRequest.getName());
        }
        if (updateRequest.getTotalAsset() != null) {
            portfolio.setTotalAsset(defaultZero(updateRequest.getTotalAsset()));
        }
        if (updateRequest.getCashBalance() != null) {
            portfolio.setCashBalance(defaultZero(updateRequest.getCashBalance()));
        }
        if (updateRequest.getRiskLevel() != null) {
            portfolio.setRiskLevel(updateRequest.getRiskLevel());
        }

        return PortfolioDTO.PortfolioResponse.fromEntity(portfolio);
    }
    //5. Portfolio 삭제
    public void deletePortfolio(Long portfolioId) {
        if (! portfolioRepository.existsById(portfolioId)) {
            throw new BusinessException(ErrorCode.PORTFOIO_NOT_FOUND);
        }
        portfolioRepository.deleteById(portfolioId);
    }
    //6. Portfolio 보유 종목 조회
    @Transactional(readOnly = true)
    public PortfolioDTO.PortfolioHoldingResponse readPortfolioHoldings(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOIO_NOT_FOUND));
        return PortfolioDTO.PortfolioHoldingResponse.fromEntity(portfolio);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private void applyHoldings(Portfolio portfolio, List<PortfolioDTO.HoldingRequest> holdingRequests) {
        if (holdingRequests == null || holdingRequests.isEmpty()) {
            return;
        }

        holdingRequests.forEach(holdingRequest -> {
            Stock stock = stockRepository.findById(holdingRequest.getStockId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.TICKER_NOT_FOUND,
                            "stockId " + holdingRequest.getStockId() + " 에 해당하는 종목을 찾을 수 없습니다."
                    ));

            Holding holding = Holding.builder()
                    .stock(stock)
                    .quantity(holdingRequest.getQuantity())
                    .avgPrice(holdingRequest.getAvgPrice())
                    .currentWeight(holdingRequest.getCurrentWeight())
                    .targetWeight(holdingRequest.getTargetWeight())
                    .build();
            portfolio.addHolding(holding);
        });
    }

}
