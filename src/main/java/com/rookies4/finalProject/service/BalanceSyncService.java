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
     */
    @Transactional
    public KisBalanceDTO syncAndGetFromKis(User user, boolean useVirtual) {
        // 1. 토큰 발급
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

        // 4. DB 동기화 실행
        syncFromKis(user.getId(), kisBalance, useVirtual);

        return kisBalance;
    }

    /**
     * KIS 잔고 기준으로 우리 DB(Portfolio/Holding) 동기화
     * - orphanRemoval=true를 활용하여 리스트에서 제거 시 DB 삭제 유도
     * - 타입 불일치 해결 (Integer, Float)
     */
    @Transactional
    public void syncFromKis(Long userId, KisBalanceDTO kisBalance, boolean useVirtual) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다. userId=" + userId
                ));

        // 1. 포트폴리오 찾기 또는 생성
        Portfolio portfolio = findOrCreateKisPortfolio(user, useVirtual);

        // KIS에서 응답받은 보유 종목 리스트
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

        // 2. [삭제] DB 포트폴리오에는 있지만 KIS 잔고에는 없는 종목 제거
        List<Holding> holdings = portfolio.getHoldings();
        holdings.removeIf(h -> {
            String code = (h.getStock() != null) ? h.getStock().getStockCode() : null;
            // 코드가 없거나, KIS 응답 맵에 없으면 삭제 대상
            boolean toDelete = code == null || !kisMap.containsKey(code);
            if (toDelete) {
                log.info("[BALANCE_SYNC] 보유 종목 삭제: code={}", code);
            }
            return toDelete;
        });

        // 3. [추가/수정] KIS 정보를 바탕으로 DB 업데이트
        for (KisBalanceDTO.KisBalanceDetail d : details) {
            String stockCode = d.getPdno();
            if (stockCode == null || stockCode.isBlank()) continue;

            // [수정] Entity 타입(Integer)에 맞춰 변환
            Integer quantity = toInteger(d.getHldgQty());
            BigDecimal avgPrice = toBigDecimal(d.getPchsAvgPric());

            // 수량이 0 이하면 보유하지 않은 것으로 간주
            if (quantity <= 0) continue;

            // 3-1. Stock 엔티티 확인 (없으면 생성)
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
                        s.setName(d.getPrdtName() != null ? d.getPrdtName() : "Unknown");
                        return stockRepository.save(s);
                    });

            // 3-2. 현재 Holding 리스트에서 해당 종목 찾기
            Holding holding = holdings.stream()
                    .filter(h -> h.getStock() != null && stockCode.equals(h.getStock().getStockCode()))
                    .findFirst()
                    .orElse(null);

            if (holding == null) {
                // 신규 추가
                holding = Holding.builder()
                        .portfolio(portfolio)
                        .stock(stock)
                        // .stockCode(stockCode) -> 삭제됨 (Entity에 없는 필드)
                        .quantity(quantity)              // Integer 타입
                        .avgPrice(avgPrice)              // BigDecimal 타입
                        .currentWeight(0.0f)             // Float 타입 초기화
                        .targetWeight(0.0f)              // Float 타입 초기화
                        .build();
                holdings.add(holding);
            } else {
                // 기존 정보 업데이트
                holding.setQuantity(quantity);
                holding.setAvgPrice(avgPrice);
            }
        }

        // 4. 포트폴리오 메타데이터(예수금 등) 업데이트
        List<KisBalanceDTO.OutputSummary> summaries = kisBalance.getOutput2();
        if (summaries != null && !summaries.isEmpty()) {
            KisBalanceDTO.OutputSummary s = summaries.get(0);
            portfolio.setTotalAsset(toBigDecimal(s.getTotEvluAmt()));   // 총평가금액
            portfolio.setCashBalance(toBigDecimal(s.getDncaTotAmt()));   // 예수금
        }

        // 5. 최종 저장
        portfolioRepository.save(portfolio);

        log.info("[BALANCE_SYNC] 잔고 동기화 완료: userId={}, portfolioId={}, 종목수={}",
                userId, portfolio.getPortfolioId(), portfolio.getHoldings().size());
    }

    private Portfolio findOrCreateKisPortfolio(User user, boolean useVirtual) {
        String name = useVirtual ? VIRTUAL_PORTFOLIO_NAME : REAL_PORTFOLIO_NAME;

        // Repository가 List를 반환한다고 가정하고 처리
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