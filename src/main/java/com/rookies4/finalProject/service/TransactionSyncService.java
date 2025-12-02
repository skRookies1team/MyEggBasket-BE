package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Transaction;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.domain.enums.TransactionStatus;
import com.rookies4.finalProject.domain.enums.TransactionType;
import com.rookies4.finalProject.dto.KisAuthDTO;
import com.rookies4.finalProject.dto.KisTransactionDto;
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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSyncService {

    private final KisAuthService kisAuthService;
    private final KisOrderHistoryService kisOrderHistoryService;
    private final TransactionRepository transactionRepository;

    /**
     * 한투(KIS)와 주문 내역을 동기화한다.
     * - 1) 토큰 발급
     * - 2) 일간 주문 내역 조회
     * - 3) DB(Transaction) 갱신
     */
    @Transactional
    public void syncUserOrdersFromKis(User user) {
        // TODO: 실제 환경에 맞게 모의/실거래 여부를 User 설정값 등에서 가져오도록 변경
        boolean useVirtual = true;

        KisAuthDTO.KisTokenResponse tokenResponse =
                kisAuthService.issueToken(useVirtual, user);

        String accessToken = tokenResponse.getAccessToken();

        KisTransactionDto kisResponse =
                kisOrderHistoryService.getDailyOrderHistory(user, accessToken, useVirtual);

        if (kisResponse == null || kisResponse.getOutput1() == null) {
            log.debug("KIS 주문 내역 없음, userId={}", user.getId());
            return;
        }

        List<KisTransactionDto.KisOrderDetail> details = kisResponse.getOutput1();

        for (KisTransactionDto.KisOrderDetail kisOrder : details) {
            upsertSingleOrder(user, kisOrder);
        }
    }

    /**
     * KIS 주문 1건을 DB Transaction 으로 upsert
     */
    private void upsertSingleOrder(User user, KisTransactionDto.KisOrderDetail kisOrder) {
        String orderNo = kisOrder.getOrderNo();
        if (orderNo == null || orderNo.isBlank()) {
            log.debug("주문번호가 없는 레코드 무시: {}", kisOrder);
            return;
        }

        Optional<Transaction> optional =
                transactionRepository.findByUser_IdAndOrderNo(user.getId(), orderNo);

        Transaction transaction = optional.orElseGet(() ->
                Transaction.builder()
                        .user(user)
                        .orderNo(orderNo)
                        // .stock(..) // TODO: 종목코드 기반 Stock 매핑이 필요하면 여기서 처리
                        .build()
        );

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
    }

    private TransactionType mapType(KisTransactionDto.KisOrderDetail kisOrder) {
        // 매도 = 01, 매수 = 02
        String code = kisOrder.getBuySellCode();
        if ("02".equals(code)) {
            return TransactionType.BUY;
        }
        return TransactionType.SELL;
    }

    // 체결 상태 결정하기 (CANCELLED, COMPLETED, PENDING)
    private TransactionStatus decideStatus(int orderQty, int filledQty, int calcelQty) {
        if (calcelQty > 0) {
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
