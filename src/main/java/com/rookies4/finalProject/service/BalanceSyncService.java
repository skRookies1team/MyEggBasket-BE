package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Holding;
import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.dto.KisBalanceDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.PortfolioRepository;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceSyncService {

    private static final String REAL_PORTFOLIO_NAME = "KIS-REAL";
    private static final String VIRTUAL_PORTFOLIO_NAME = "KIS-VIRTUAL";

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;

    private final KisAuthService kisAuthService;
    private final KisBalanceService kisBalanceService;

    /**
     * KIS 잔고 조회 + DB 동기화 + 원본 KIS 응답 반환
     *
     * 트랜잭션 분리 전략:
     * 1. 외부 API 호출은 트랜잭션 밖에서 실행 (DB 커넥션 낭비 방지)
     * 2. DB 작업만 최소 범위의 트랜잭션으로 처리
     */
    public KisBalanceDTO syncAndGetFromKis(User user, boolean useVirtual) {
        // STEP 1: 토큰 발급 (별도 트랜잭션, 빠르게 커밋됨)
        KisAuthTokenDTO.KisTokenResponse tokenResponse =
                kisAuthService.issueToken(useVirtual, user);

        String accessToken = tokenResponse.getAccessToken();
        log.info("[SYNC] 잔고 동기화 시작 - userId: {}, virtual: {}", user.getId(), useVirtual);

        // STEP 2: KIS API 호출 (트랜잭션 밖에서 실행 - 중요!)
        // 외부 API 응답 대기 중에 DB 커넥션을 잡고 있지 않음
        KisBalanceDTO kisBalance =
                kisBalanceService.getBalanceFromKis(user, accessToken, useVirtual);

        if (kisBalance == null) {
            log.warn("[SYNC] KIS 응답이 null - userId: {}", user.getId());
            return null;
        }

        // STEP 3: DB 동기화 (새로운 트랜잭션으로 실행)
        try {
            syncFromKis(user.getId(), kisBalance, useVirtual);
            log.info("[SYNC] 동기화 완료 - userId: {}", user.getId());
        } catch (Exception e) {
            log.error("[SYNC] 동기화 실패 - userId: {}, error: {}", user.getId(), e.getMessage());
            // 동기화 실패해도 KIS 원본 데이터는 반환 (조회 기능은 정상 작동)
        }

        return kisBalance;
    }

    /**
     * KIS 잔고 기준으로 DB 동기화
     *
     * 트랜잭션 전략:
     * - REQUIRES_NEW: 독립적인 트랜잭션으로 실행
     * - 외부 API 호출과 분리되어 있어 커넥션 점유 시간 최소화
     * - 실패해도 상위 메서드에 영향 없음
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncFromKis(Long userId, KisBalanceDTO kisBalance, boolean useVirtual) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다. userId=" + userId
                ));

        // KIS가 실패 응답을 준 경우 동기화를 중단한다.
        if (kisBalance.getRtCd() != null && !"0".equals(kisBalance.getRtCd())) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "KIS 잔고 응답이 실패 상태입니다.");
        }

        // 1. 포트폴리오 찾기 또는 생성
        Portfolio portfolio = findOrCreateKisPortfolio(user, useVirtual);

        List<KisBalanceDTO.KisBalanceDetail> details = kisBalance.getOutput1();
        if (details == null) details = new ArrayList<>();

        // KIS 응답 데이터를 Map으로 변환 (검색 속도 향상)
        Map<String, KisBalanceDTO.KisBalanceDetail> kisMap = details.stream()
                .filter(d -> d.getPdno() != null && !d.getPdno().isBlank())
                .collect(Collectors.toMap(
                        KisBalanceDTO.KisBalanceDetail::getPdno,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        // 2. [삭제] DB에는 있지만 KIS에는 없거나, 또는 quantity <= 0인 종목 제거
        List<Holding> holdings = portfolio.getHoldings();
        int removedCount = holdings.size();
        holdings.removeIf(h -> {
            String code = (h.getStock() != null) ? h.getStock().getStockCode() : null;
            // KIS에 없거나, quantity <= 0인 holding 제거
            return code == null || !kisMap.containsKey(code) || (h.getQuantity() != null && h.getQuantity() <= 0);
        });
        removedCount -= holdings.size();

        // 3. [추가/수정] KIS 정보를 바탕으로 DB 업데이트
        int addedCount = 0;
        int updatedCount = 0;

        for (KisBalanceDTO.KisBalanceDetail d : details) {
            String stockCode = d.getPdno();
            if (stockCode == null || stockCode.isBlank()) continue;

            Integer quantity = toInteger(d.getHldgQty());
            BigDecimal avgPrice = toBigDecimal(d.getPchsAvgPric());

            if (quantity <= 0) continue;

            // ⭐ Stock 조회/생성 (별도 트랜잭션으로 처리)
            Stock stock = getOrCreateStock(stockCode, d.getPrdtName());

            // Holding 찾기/생성
            Holding holding = holdings.stream()
                    .filter(h -> h.getStock() != null && stockCode.equals(h.getStock().getStockCode()))
                    .findFirst()
                    .orElse(null);

            if (holding == null) {
                holding = Holding.builder()
                        .portfolio(portfolio)
                        .stock(stock)
                        .quantity(quantity)
                        .avgPrice(avgPrice)
                        .currentWeight(0.0f)
                        .targetWeight(0.0f)
                        .build();
                holdings.add(holding);
                addedCount++;
            } else {
                holding.setQuantity(quantity);
                holding.setAvgPrice(avgPrice);
                updatedCount++;
            }
        }

        // 4. 포트폴리오 메타데이터 업데이트
        List<KisBalanceDTO.OutputSummary> summaries = kisBalance.getOutput2();
        if (summaries != null && !summaries.isEmpty()) {
            KisBalanceDTO.OutputSummary s = summaries.get(0);
            portfolio.setTotalAsset(toBigDecimal(s.getTotEvluAmt()));
            portfolio.setCashBalance(toBigDecimal(s.getDncaTotAmt()));
        }

        // 5. 저장 (KIS 포트폴리오)
        portfolioRepository.save(portfolio);

        // 6. 동일 사용자, 동일 종목을 이미 보유한 다른 포트폴리오의 수량/평단만 동기화한다.
        syncExistingHoldingsToOtherPortfolios(user, portfolio);

        log.info("[SYNC] 동기화 완료 - 추가: {}, 수정: {}, 삭제: {}", addedCount, updatedCount, removedCount);
    }

    /**
     * 사용자의 모든 포트폴리오에서 KIS에 없거나 quantity≤0인 종목을 제거한다.
     * - KIS에 없는 종목: 전량 매도한 것
     * - quantity≤0인 종목: 보유하지 않는 것
     */
    private void syncExistingHoldingsToOtherPortfolios(User user, Portfolio kisPortfolio) {
        List<Portfolio> portfolios = portfolioRepository.findByUser(user);
        if (portfolios == null || portfolios.isEmpty()) {
            return;
        }

        Map<String, Holding> sourceHoldings = kisPortfolio.getHoldings().stream()
                .filter(h -> h.getStock() != null && h.getStock().getStockCode() != null && h.getQuantity() != null && h.getQuantity() > 0)
                .collect(Collectors.toMap(
                        h -> h.getStock().getStockCode(),
                        Function.identity(),
                        (a, b) -> a
                ));

        for (Portfolio p : portfolios) {
            if (p.getPortfolioId().equals(kisPortfolio.getPortfolioId())) {
                continue; // 본인 제외
            }

            List<Holding> targetHoldings = p.getHoldings();
            if (targetHoldings == null || targetHoldings.isEmpty()) {
                continue; // 기존 보유가 없는 포트폴리오는 건너뜀
            }

            // KIS에 없거나, quantity<=0인 종목 제거 (전량 매도 반영)
            targetHoldings.removeIf(h -> h.getStock() == null
                    || h.getStock().getStockCode() == null
                    || !sourceHoldings.containsKey(h.getStock().getStockCode())
                    || (h.getQuantity() != null && h.getQuantity() <= 0));

            // 존재하는 종목은 수량/평단 동기화
            for (Holding tgt : targetHoldings) {
                String code = tgt.getStock().getStockCode();
                Holding src = sourceHoldings.get(code);
                if (src != null) {
                    tgt.setQuantity(src.getQuantity());
                    tgt.setAvgPrice(src.getAvgPrice());
                }
            }

            portfolioRepository.save(p);
        }
    }

    /**
     * Stock 조회 또는 생성
     *
     * REQUIRES_NEW 사용 이유:
     * - Stock 생성 실패해도 전체 동기화가 중단되지 않음
     * - 여러 Holding이 같은 Stock을 참조할 때 중복 생성 방지
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected Stock getOrCreateStock(String stockCode, String stockName) {
        return stockRepository.findByStockCode(stockCode)
                .map(existing -> {
                    // 종목명이 바뀌었으면 업데이트
                    if (stockName != null && !stockName.isBlank() && !stockName.equals(existing.getName())) {
                        existing.setName(stockName);
                        stockRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    Stock stock = new Stock();
                    stock.setStockCode(stockCode);
                    stock.setName(stockName != null ? stockName : "Unknown");
                    return stockRepository.save(stock);
                });
    }

    private Portfolio findOrCreateKisPortfolio(User user, boolean useVirtual) {
        String name = useVirtual ? VIRTUAL_PORTFOLIO_NAME : REAL_PORTFOLIO_NAME;

        return portfolioRepository.findByUser(user).stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Portfolio p = new Portfolio();
                    p.setUser(user);
                    p.setName(name);
                    p.setTotalAsset(BigDecimal.ZERO);
                    p.setCashBalance(BigDecimal.ZERO);
                    return portfolioRepository.save(p);
                });
    }

    private BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer toInteger(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}