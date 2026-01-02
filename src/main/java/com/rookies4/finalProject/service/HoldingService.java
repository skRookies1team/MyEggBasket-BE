package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Holding;
import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.dto.HoldingDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.HoldingRepository;
import com.rookies4.finalProject.repository.PortfolioRepository;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.PathVariable;

@Service
@RequiredArgsConstructor
@Transactional
public class HoldingService {
    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final PortfolioRepository portfolioRepository;

    //1. 보유 종목 추가
    public HoldingDTO.HoldingResponse addHolding(Long portfolioId, HoldingDTO.HoldingRequest request){
        Stock stock = stockRepository.findByStockCode(request.getStockCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND));

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        // 권한 확인: 포트폴리오 소유자만 보유 종목 추가 가능
        validatePortfolioOwnership(portfolio);

        // 이미 같은 종목이 있는지 확인
        holdingRepository.findByPortfolioAndStock(portfolio, stock)
                .ifPresent(existingHolding -> {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, 
                        "해당 포트폴리오에 이미 존재하는 종목입니다. 수정 기능을 사용해주세요.");
                });

        Holding holding = Holding.builder()
                .portfolio(portfolio)
                .stock(stock)
                .quantity(request.getQuantity())
                .avgPrice(request.getAvgPrice())
                .currentWeight(request.getCurrentWeight())
                .targetWeight(request.getTargetWeight())
                .build();

        Holding savedHolding = holdingRepository.save(holding);
        
        // 저장된 Holding의 Stock이 제대로 초기화되었는지 확인
        if (savedHolding.getStock() != null) {
            savedHolding.getStock().getStockCode();
            savedHolding.getStock().getName();
        }

        return HoldingDTO.HoldingResponse.fromEntity(savedHolding);
    }

    //2. 포트폴리오의 모든 보유 종목 조회
    @Transactional(readOnly = true)
    public List<HoldingDTO.HoldingResponse> readHoldings(Long portfolioId){
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        // 권한 확인: 포트폴리오 소유자만 조회 가능
        validatePortfolioOwnership(portfolio);

        List<Holding> holdings = holdingRepository.findByPortfolio(portfolio);
        
        // LAZY 로딩된 Stock 엔티티들을 트랜잭션 내에서 초기화하여 JSON 직렬화 시 오류 방지
        // 그리고 quantity > 0인 것만 반환
        return holdings.stream()
                .filter(holding -> holding.getQuantity() != null && holding.getQuantity() > 0)
                .peek(holding -> {
                    if (holding.getStock() != null) {
                        // Stock의 기본 필드들을 접근하여 초기화
                        holding.getStock().getStockCode();
                        holding.getStock().getName();
                    }
                })
                .map(HoldingDTO.HoldingResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
//    //3. 특정 보유 종목 조회
//    @Transactional(readOnly = true)
//    public HoldingDTO.HoldingResponse readHoldingStock(Long holdingId){
//        Holding holding = holdingRepository.findById(holdingId)
//                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "보유 종목을 찾을 수 없습니다."));
//
//        // 권한 확인: 포트폴리오 소유자만 조회 가능
//        validatePortfolioOwnership(holding.getPortfolio());
//
//        return HoldingDTO.HoldingResponse.fromEntity(holding);
//    }
//
    //4. 보유 종목 수정
    public HoldingDTO.HoldingResponse updateHolding(Long portfolioId, Long holdingId, HoldingDTO.HoldingRequest request){
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));
        Holding holding = holdingRepository.findByPortfolioAndHoldingId(portfolio, holdingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "보유 종목을 찾을 수 없습니다."));

        // 권한 확인: 포트폴리오 소유자만 수정 가능
        validatePortfolioOwnership(holding.getPortfolio());

        if (request.getQuantity() != null) {
            holding.setQuantity(request.getQuantity());
        }
        if (request.getAvgPrice() != null) {
            holding.setAvgPrice(request.getAvgPrice());
        }
        if (request.getCurrentWeight() != null) {
            holding.setCurrentWeight(request.getCurrentWeight());
        }
        if (request.getTargetWeight() != null) {
            holding.setTargetWeight(request.getTargetWeight());
        }

        return HoldingDTO.HoldingResponse.fromEntity(holding);
    }

    //5. 포트폴리오에 있는 보유 종목 삭제
    public void deleteHolding(Long portfolioId, Long holdingId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));
        Holding holding = holdingRepository.findByPortfolioAndHoldingId(portfolio, holdingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "보유 종목을 찾을 수 없습니다."));

        // 권한 확인: 포트폴리오 소유자만 삭제 가능
        validatePortfolioOwnership(holding.getPortfolio());

        holdingRepository.deleteById(holdingId);
    }
    
    // 권한 확인 헬퍼 메서드
    private void validatePortfolioOwnership(Portfolio portfolio) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }
        
        if (!portfolio.getUser().getId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "해당 포트폴리오에 대한 접근 권한이 없습니다.");
        }
    }
}
