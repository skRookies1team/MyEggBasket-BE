package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.Transaction;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.domain.enums.TransactionStatus;
import com.rookies4.finalProject.domain.enums.TransactionType;
import com.rookies4.finalProject.dto.KisAuthTokenDTO;
import com.rookies4.finalProject.dto.KisTransactionDTO;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSyncService {

    private final KisAuthService kisAuthService;
    private final KisTransactionService kisTransactionService;
    private final TransactionRepository transactionRepository;
    private final StockRepository stockRepository;

    /**
     * 한투(KIS)와 주문 내역을 동기화한다.
     * - 1) 토큰 발급/재사용
     * - 2) 일간 주문 내역 조회
     * - 3) DB(Transaction Entity) upsert
     */
    public void syncUserOrdersFromKis(User user, boolean useVirtualServer) {

        KisAuthTokenDTO.KisTokenResponse tokenResponse =
                kisAuthService.issueToken(useVirtualServer, user);

        String accessToken = tokenResponse.getAccessToken();
        log.info("[SYNC] 토큰 준비 완료, userId={}", user.getId());

        KisTransactionDTO kisResponse =
                kisTransactionService.getDailyOrderHistory(user, accessToken, useVirtualServer);

        // 1) 응답 자체가 null
        if (kisResponse == null) {
            log.warn("[SYNC] KIS 주문 내역 응답이 null, userId={}", user.getId());
            return;
        }

        // 2) output1 이 null
        List<KisTransactionDTO.KisOrderDetail> details = kisResponse.getOutput1();
        if (details == null) {
            log.warn("[SYNC] KIS 주문 내역 output1 == null, userId={}", user.getId());
            return;
        }

        // 3) 주문 내역 0건
        if (details.isEmpty()) {
            log.info("[SYNC] KIS 주문 내역 0건, userId={}", user.getId());
            return;
        }

        // 4) 정상적으로 n건 수신
        log.info("[SYNC] KIS 주문 내역 수신: userId={}, count={}", user.getId(), details.size());

        for (KisTransactionDTO.KisOrderDetail kisOrder : details) {
            log.debug("[SYNC] 수신 주문: odno={}, pdno={}, buySellCode={}, qty={}, filledQty={}",
                    kisOrder.getOrderNo(),
                    kisOrder.getStockCode(),
                    kisOrder.getBuySellCode(),
                    kisOrder.getOrderQty(),
                    kisOrder.getFilledQty());

            upsertSingleOrder(user, kisOrder);
        }
    }

    // KIS 주문 1건을 DB Transaction 으로 upsert(중복키 존재 -> 업데이트, 중복키 부재 -> 삽입)
    private void upsertSingleOrder(User user, KisTransactionDTO.KisOrderDetail kisOrder) {
        String orderNo = kisOrder.getOrderNo();
        if (orderNo == null || orderNo.isBlank()) {
            log.warn("[SYNC] 주문번호 없는 레코드 무시: userId={}, data={}", user.getId(), kisOrder);
            return;
        }

        // KIS 응답으로부터 Stock upsert (중복키 존재 -> 업데이트, 중복키 부재 -> 삽입)
        Stock stock = upsertStockFromKis(kisOrder);

        Optional<Transaction> optional =
                transactionRepository.findByUser_IdAndOrderNo(user.getId(), orderNo);

        Transaction transaction = optional.orElseGet(() ->
                Transaction.builder()
                        .user(user)
                        .orderNo(orderNo)
                        .build()
        );

        if (stock != null) {
            transaction.setStock(stock);
        }

        // 매수/매도 타입
        TransactionType type = mapType(kisOrder);
        transaction.setType(type);

        int orderQty = parseIntSafe(kisOrder.getOrderQty());
        int filledQty = parseIntSafe(kisOrder.getFilledQty());
        int cancelQty = parseIntSafe(kisOrder.getCancelQty());
        BigDecimal avgPrice = parseBigDecimalSafe(kisOrder.getAvgPrice());

        transaction.setQuantity(orderQty);
        transaction.setFilledQuantity(filledQty);
        transaction.setPrice(avgPrice);

        // 체결 시각
        LocalDateTime executedAt = parseKisDateTime(kisOrder.getOrderDate(), kisOrder.getOrderTime());
        transaction.setExecutedAt(executedAt);

        // 상태 결정
        TransactionStatus status = decideStatus(orderQty, filledQty, cancelQty);
        transaction.setStatus(status);

        transactionRepository.save(transaction);
        log.info("[SYNC] Transaction upsert 완료: userId={}, orderNo={}, status={}",
                user.getId(), orderNo, transaction.getStatus());
    }

    private Stock upsertStockFromKis(KisTransactionDTO.KisOrderDetail kisOrder) {
        String stockCode = kisOrder.getStockCode();
        if (stockCode == null || stockCode.isBlank()) {
            return null;
        }

        String stockName = kisOrder.getStockName();

        return stockRepository.findById(stockCode)
                .map(existing -> {
                    // 종목명이 바뀌었으면 업데이트
                    if (stockName != null && !stockName.isBlank()
                            && !stockName.equals(existing.getName())) {
                        existing.setName(stockName);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    Stock stock = Stock.builder()
                            .stockCode(stockCode)
                            .name(stockName != null ? stockName : "")
                            .build();
                    return stockRepository.save(stock);
                });
    }

    private TransactionType mapType(KisTransactionDTO.KisOrderDetail kisOrder) {
        // 매도 = 01, 매수 = 02
        String code = kisOrder.getBuySellCode();
        if ("02".equals(code)) {
            return TransactionType.BUY;
        }
        return TransactionType.SELL;
    }

    // 체결 상태 결정하기 (CANCELLED, COMPLETED, PENDING)
    private TransactionStatus decideStatus(int orderQty, int filledQty, int cancelQty) {
        if (cancelQty > 0) {
            return TransactionStatus.CANCELLED;
        }
        if (orderQty > 0 && filledQty >= orderQty) {
            return TransactionStatus.COMPLETED;
        }
        return TransactionStatus.PENDING;
    }

    private int parseIntSafe(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("정수 파싱 실패 value='{}'", value);
            return 0;
        }
    }

    private BigDecimal parseBigDecimalSafe(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.warn("BigDecimal 파싱 실패 value='{}'", value);
            return BigDecimal.ZERO;
        }
    }

    private LocalDateTime parseKisDateTime(String date, String time) {
        // date: yyyyMMdd, time: HHmmss 형식
        if (date == null || time == null || date.isBlank() || time.isBlank()) {
            return null;
        }

        try {
            LocalDate d = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalTime t = LocalTime.parse(time, DateTimeFormatter.ofPattern("HHmmss"));
            return LocalDateTime.of(d, t);
        } catch (Exception e) {
            log.warn("KIS 일시 파싱 실패 date='{}', time='{}'", date, time);
            return null;
        }
    }
}
