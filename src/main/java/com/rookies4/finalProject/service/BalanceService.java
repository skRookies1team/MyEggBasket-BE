package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.BalanceDTO;
import com.rookies4.finalProject.dto.KisBalanceDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final UserRepository userRepository;
    private final BalanceSyncService balanceSyncService;

    /**
     * 로그인 유저 기준 잔고 조회
     * - 요청 시마다 KIS 와 동기화 시도
     * - 동기화 실패 시, 일단 KIS 응답이 없으면 에러 / 필요하면 DB 기반 fallback 추가 가능
     */
    @Transactional
    public BalanceDTO.Response getUserBalance(Long userId, boolean useVirtual) {

        // 0. 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다. userId=" + userId
                ));

        KisBalanceDTO kisBalance = null;

        // 1. KIS 동기화 (토큰 발급 + KIS 호출 + DB 동기화 모두 BalanceSyncService 에서 처리)
        try {
            kisBalance = balanceSyncService.syncAndGetFromKis(user, useVirtual);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[BALANCE] KIS 잔고 동기화 실패, KIS 응답 없이 DB 데이터만 사용 예정. userId={}, msg={}",
                    userId, e.getMessage());
        }

        // 2. KIS 응답 기준 DTO 매핑
        if (kisBalance != null) {
            return toBalanceResponse(kisBalance);
        }

        // 3. KIS 응답이 완전히 없을 때의 fallback 전략
        throw new BusinessException(
                ErrorCode.KIS_API_ERROR,
                "잔고 정보를 조회할 수 없습니다. (KIS 응답 없음)"
        );
    }

    private BalanceDTO.Response toBalanceResponse(KisBalanceDTO kis) {
        log.info("[BALANCE] KisBalanceDTO raw = {}", kis);
        log.info("[BALANCE] output1 size = {}, output2 size = {}",
                kis.getOutput1() == null ? null : kis.getOutput1().size(),
                kis.getOutput2() == null ? null : kis.getOutput2().size());

        BalanceDTO.Response resp = new BalanceDTO.Response();

        // summary 세팅 (output2[0] 기준)
        BalanceDTO.Summary summary = new BalanceDTO.Summary();
        List<KisBalanceDTO.OutputSummary> output2 = kis.getOutput2();

        if (output2 != null && !output2.isEmpty()) {
            KisBalanceDTO.OutputSummary s = output2.get(0);
            summary.setTotalCashAmount(toBigDecimal(s.getDncaTotAmt()));            // 예수금총금액
            summary.setD1CashAmount(toBigDecimal(s.getNxdyExccAmt()));              // D+1 예수금
            summary.setD2CashAmount(toBigDecimal(s.getPrvsRcdlExccAmt()));          // D+2 예수금
            summary.setTotalEvaluationAmount(toBigDecimal(s.getTotEvluAmt()));      // 총평가금액
            summary.setTotalProfitLossAmount(toBigDecimal(s.getEvluPflsSmtlAmt())); // 평가손익합계금액
            summary.setTotalPurchaseAmount(toBigDecimal(s.getPchsAmtSmtlAmt()));    // 매입금액합계금액
            summary.setNetAssetAmount(toBigDecimal(s.getNassAmt()));                // 순자산금액
            summary.setProfitRate(toBigDecimal(s.getAsstIcdcErngRt()));             // 자산증감수익율
        }

        resp.setSummary(summary);

        // holdings 세팅 (output1 기준, KIS 원본 잔고)
        List<KisBalanceDTO.KisBalanceDetail> output1 = kis.getOutput1();
        if (output1 != null && !output1.isEmpty()) {
            List<BalanceDTO.Holding> holdings = output1.stream()
                    .map(this::toHolding)
                    .toList();
            resp.setHoldings(holdings);
        }

        return resp;
    }

    private BalanceDTO.Holding toHolding(KisBalanceDTO.KisBalanceDetail d) {
        BalanceDTO.Holding h = new BalanceDTO.Holding();
        h.setStockCode(d.getPdno());
        h.setStockName(d.getPrdtName());
        h.setQuantity(toInteger(d.getHldgQty()));              // BalanceDTO 쪽은 Long 사용
        h.setOrderableQuantity(toInteger(d.getOrdPsblQty()));
        h.setAvgPrice(toBigDecimal(d.getPchsAvgPric()));
        h.setCurrentPrice(toBigDecimal(d.getPrpr()));
        h.setEvaluationAmount(toBigDecimal(d.getEvluAmt()));
        h.setProfitLossAmount(toBigDecimal(d.getEvluPflsAmt()));
        h.setProfitLossRate(toBigDecimal(d.getEvluPflsRt()));
        return h;
    }

    private BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[BALANCE] BigDecimal 변환 실패 value={}", value);
            return BigDecimal.ZERO;
        }
    }

    private Integer toInteger(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[BALANCE] Integer 변환 실패 value={}", value);
            return 0;
        }
    }
}
