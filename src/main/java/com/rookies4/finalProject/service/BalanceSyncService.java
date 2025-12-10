package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Holding;
import com.rookies4.finalProject.domain.entity.Portfolio;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.dto.KisBalanceDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.HoldingRepository;
import com.rookies4.finalProject.repository.PortfolioRepository;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.repository.UserRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceSyncService {

    private static final String REAL_PORTFOLIO_NAME = "KIS-REAL";
    private static final String VIRTUAL_PORTFOLIO_NAME = "KIS-VIRTUAL";

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;

    private final KisAuthService kisAuthService;
    private final KisBalanceService kisBalanceService;

    /**
     * KIS 잔고 기준으로 우리 DB(Portfolio/Holding) 동기화
     */
    @Transactional
    public void syncFromKis(Long userId, KisBalanceDTO kisBalance, boolean useVirtual) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다. userId=" + userId
                ));

        // 1. 이 유저의 "KIS용 포트폴리오" 찾거나 생성
        Portfolio portfolio = findOrCreateKisPortfolio(user, useVirtual);

        // 2. 기존 Holding 을 stockCode 기준으로 맵핑
        List<Holding> existingHoldings = holdingRepository.findByPortfolio(portfolio);
        Map<String, Holding> holdingMap = new HashMap<>();
        for (Holding h : existingHoldings) {
            if (h.getStock() != null && h.getStock().getStockCode() != null) {
                holdingMap.put(h.getStock().getStockCode(), h);
            }
        }

        // 이번 KIS 응답에 등장한 종목들
        Set<String> seenStockCodes = new HashSet<>();

        List<KisBalanceDTO.KisBalanceDetail> details = kisBalance.getOutput1();
        if (details == null || details.isEmpty()) {
            log.info("[BALANCE_SYNC] KIS 잔고에 보유 종목이 없습니다. userId={}", userId);
        } else {
            for (KisBalanceDTO.KisBalanceDetail d : details) {
                String stockCode = d.getPdno();
                if (stockCode == null || stockCode.isBlank()) {
                    log.warn("[BALANCE_SYNC] 종목코드(pdno) 없음, 레코드 무시: {}", d);
                    continue;
                }
                seenStockCodes.add(stockCode);

                Integer quantity = toInteger(d.getHldgQty());
                BigDecimal avgPrice = toBigDecimal(d.getPchsAvgPric());

                // 0주면 보유하지 않는 것으로 보고 건너뛰는 정책
                if (quantity == null || quantity <= 0) {
                    continue;
                }

                // Stock upsert
                Stock stock = stockRepository.findByStockCode(stockCode)
                        .map(existing -> {
                            if (d.getPrdtName() != null && !d.getPrdtName().isBlank()) {
                                existing.setName(d.getPrdtName());
                            }
                            return existing;
                        })
                        .orElseGet(() -> {
                            Stock s = new Stock();
                            s.setStockCode(stockCode);
                            s.setName(d.getPrdtName() != null ? d.getPrdtName() : "");
                            return stockRepository.save(s);
                        });

                // Holding upsert
                Holding holding = holdingMap.get(stockCode);
                if (holding == null) {
                    holding = new Holding();
                    holding.setPortfolio(portfolio);
                    holding.setStock(stock);
                }

                holding.setQuantity(quantity);   // Holding.quantity = Integer
                holding.setAvgPrice(avgPrice);   // Holding.avgPrice = BigDecimal

                holdingRepository.save(holding);
                holdingMap.put(stockCode, holding);
            }
        }

        // 3. KIS 응답에 더 이상 없는 종목 정리 (정책: 삭제)
        for (Holding h : existingHoldings) {
            String code = (h.getStock() != null) ? h.getStock().getStockCode() : null;
            if (code == null) continue;
            if (!seenStockCodes.contains(code)) {
                holdingRepository.delete(h);
            }
        }

        // 4. 포트폴리오 요약 값도 KIS summary 기반으로 업데이트
        List<KisBalanceDTO.OutputSummary> summaries = kisBalance.getOutput2();
        if (summaries != null && !summaries.isEmpty()) {
            KisBalanceDTO.OutputSummary s = summaries.get(0);
            portfolio.setTotalAsset(toBigDecimal(s.getTotEvluAmt()));   // 총평가금액
            portfolio.setCashBalance(toBigDecimal(s.getDncaTotAmt()));   // 예수금
            portfolioRepository.save(portfolio);
        }

        log.info("[BALANCE_SYNC] 잔고 동기화 완료: userId={}, portfolioId={}",
                userId, portfolio.getPortfolioId());
    }

    /**
     * KIS 잔고 조회 + DB 동기화 + 원본 KIS 응답 반환
     * - TransactionSyncService.syncUserOrdersFromKis 와 대칭 역할
     */
    @Transactional
    public KisBalanceDTO syncAndGetFromKis(User user, boolean useVirtual) {

        KisAuthTokenDTO.KisTokenResponse tokenResponse =
                kisAuthService.issueToken(useVirtual, user);

        String accessToken = tokenResponse.getAccessToken();
        log.info("[SYNC] 토큰 준비 완료, userId={}", user.getId());

        // 2. KIS 잔고 조회
        KisBalanceDTO kisBalance =
                kisBalanceService.getBalanceFromKis(user, accessToken, useVirtual);

        // 3. 응답 null 처리
        if (kisBalance == null) {
            log.warn("[BALANCE_SYNC] KIS 잔고 응답이 null, userId={}", user.getId());
            return null;
        }

        // 4. DB 동기화
        syncFromKis(user.getId(), kisBalance, useVirtual);

        // 5. BalanceService 에서 DTO 매핑에 쓰도록 원본 리턴
        return kisBalance;
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
                    return portfolioRepository.save(p);
                });
    }

    private BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[BALANCE_SYNC] BigDecimal 변환 실패 value={}", value);
            return BigDecimal.ZERO;
        }
    }

    private Integer toInteger(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[BALANCE_SYNC] Integer 변환 실패 value={}", value);
            return 0;
        }
    }
}