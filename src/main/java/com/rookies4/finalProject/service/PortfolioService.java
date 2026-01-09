package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.PortfolioDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.PortfolioRepository;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;

    // 1. Portfolio 생성
    public PortfolioDTO.PortfolioResponse createPortfolio(PortfolioDTO.PortfolioRequest request) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "로그인한 사용자를 찾을 수 없습니다."));

        // 사용자별로 동일한 이름의 포트폴리오 존재 여부 확인
        if (portfolioRepository.existsByNameAndUser(request.getName(), user)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "동일한 이름의 포트폴리오가 이미 존재합니다.");
        }

        Portfolio portfolio = Portfolio.builder()
                .user(user)
                .name(request.getName())
                .totalAsset(defaultZero(request.getTotalAsset()))
                .cashBalance(defaultZero(request.getCashBalance()))
                .riskLevel(request.getRiskLevel())
                .build();

        Portfolio saved = portfolioRepository.save(portfolio);
        log.info("[Portfolio] 포트폴리오 생성 성공 - UserId: {}, PortfolioName: {}", user.getId(), request.getName());
        return PortfolioDTO.PortfolioResponse.fromEntity(saved);
    }

    // 2. Portfolio 전체 조회 (현재 사용자의 포트폴리오만)
    @Transactional(readOnly = true)
    public List<PortfolioDTO.PortfolioResponse> readPortfolios() {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "로그인한 사용자를 찾을 수 없습니다."));

        List<PortfolioDTO.PortfolioResponse> portfolios = portfolioRepository.findByUser(user)
                .stream()
                .map(PortfolioDTO.PortfolioResponse::fromEntity)
                .collect(Collectors.toList());

        log.info("[Portfolio] 포트폴리오 목록 조회 성공 - UserId: {}, Count: {}", user.getId(), portfolios.size());
        return portfolios;
    }

    // 3. Portfolio 상세 조회
    @Transactional(readOnly = true)
    public PortfolioDTO.PortfolioResponse readPortfolio(Long portfolioId) {
        // [Optimized] EntityGraph를 사용하여 한 번의 쿼리로 연관 데이터를 모두 조회
        Portfolio portfolio = portfolioRepository.findByIdWithDetails(portfolioId)
                // 만약 EntityGraph 조회 실패 시(혹은 fallback 필요 시) 기본 조회 사용
                .orElse(portfolioRepository.findById(portfolioId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND)));

        // 권한 확인: 포트폴리오 소유자만 조회 가능
        validatePortfolioOwnership(portfolio);

        // N+1 문제 해결로 인해 별도의 초기화 로직 및 루프 불필요

        log.info("[Portfolio] 포트폴리오 상세 조회 성공 - UserId: {}, PortfolioId: {}", portfolio.getUser().getId(), portfolioId);
        return PortfolioDTO.PortfolioResponse.fromEntity(portfolio);
    }

    // 4. Portfolio 수정
    public PortfolioDTO.PortfolioResponse updatePortfolio(Long portfolioId,
            PortfolioDTO.PortfolioRequest updateRequest) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        // 권한 확인: 포트폴리오 소유자만 수정 가능
        validatePortfolioOwnership(portfolio);

        Long currentUserId = SecurityUtil.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "로그인한 사용자를 찾을 수 없습니다."));

        if (updateRequest.getName() != null && !updateRequest.getName().isBlank()) {
            // 이름이 변경되는 경우, 현재 사용자 내에서 중복 확인
            if (!updateRequest.getName().equals(portfolio.getName()) &&
                    portfolioRepository.existsByNameAndUser(updateRequest.getName(), user)) {
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

        log.info("[Portfolio] 포트폴리오 수정 성공 - UserId: {}, PortfolioId: {}", portfolio.getUser().getId(), portfolioId);
        return PortfolioDTO.PortfolioResponse.fromEntity(portfolio);
    }

    // 5. Portfolio 삭제
    public void deletePortfolio(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        // 권한 확인: 포트폴리오 소유자만 삭제 가능
        validatePortfolioOwnership(portfolio);

        log.info("[Portfolio] 포트폴리오 삭제 성공 - UserId: {}, PortfolioId: {}", portfolio.getUser().getId(), portfolioId);
        portfolioRepository.deleteById(portfolioId);
    }

    // 6. 포트폴리오 보유종목 조회
    @Transactional(readOnly = true)
    public PortfolioDTO.PortfolioHoldingResponse readPortfolioHoldings(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        // 권한 확인: 포트폴리오 소유자만 조회 가능
        validatePortfolioOwnership(portfolio);

        log.info("[Portfolio] 포트폴리오 보유종목 조회 성공 - UserId: {}, PortfolioId: {}", portfolio.getUser().getId(),
                portfolioId);
        return PortfolioDTO.PortfolioHoldingResponse.fromEntity(portfolio);
    }

    // 헬퍼 메서드: BigDecimal이 null인 경우 0으로 변환
    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
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
