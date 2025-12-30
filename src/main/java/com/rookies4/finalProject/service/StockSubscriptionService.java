package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.StockSubscription;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.StockSubscriptionDTO;
import com.rookies4.finalProject.dto.kafka.SubscriptionEventDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.InterestStockRepository; // [추가]
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.repository.StockSubscriptionRepository;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.kafka.SubscriptionEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream; // [추가]

/**
 * 종목 구독 서비스
 * 사용자가 조회 중인(구독 중인) 종목을 관리합니다.
 * 구독/해지 시 Kafka 이벤트를 발행하여 StockCollector에게 알립니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StockSubscriptionService {

    private final StockSubscriptionRepository stockSubscriptionRepository;
    private final InterestStockRepository interestStockRepository; // [추가] 관심 종목 Repo 주입
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final SubscriptionEventProducer subscriptionEventProducer;

    // ... (subscribe, unsubscribe 메서드 기존과 동일) ...

    public StockSubscriptionDTO.SubscriptionResponse subscribe(StockSubscriptionDTO.SubscriptionRequest request) {
        User user = getCurrentUser();
        String stockCode = request.getStockCode();
        String type = request.getType();

        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND,
                        "구독할 종목을 찾을 수 없습니다: " + stockCode));

        StockSubscription saved = null;

        if ("VIEW".equalsIgnoreCase(type)) {
            log.info("[StockSubscription] 상세 페이지 조회(VIEW) 요청 - User: {}, Stock: {}", user.getId(), stockCode);
        } else {
            if (stockSubscriptionRepository.existsByUserAndStock(user, stock)) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "이미 구독 중인 종목입니다.");
            }

            StockSubscription subscription = StockSubscription.builder()
                    .user(user)
                    .stock(stock)
                    .stockName(stock.getName())
                    .build();

            saved = stockSubscriptionRepository.save(subscription);
            log.info("[StockSubscription] 관심 종목(INTEREST) 저장 완료 - User: {}, Stock: {}", user.getId(), stockCode);
        }

        SubscriptionEventDTO event = SubscriptionEventDTO.builder()
                .userId(user.getId())
                .stockCode(stockCode)
                .eventType(SubscriptionEventDTO.EventType.SUBSCRIBE)
                .subType(type != null ? type : "VIEW")
                .timestamp(LocalDateTime.now())
                .build();

        subscriptionEventProducer.sendSubscriptionEvent(event);

        if (saved != null) {
            return StockSubscriptionDTO.SubscriptionResponse.fromEntity(saved);
        } else {
            return StockSubscriptionDTO.SubscriptionResponse.builder()
                    .stockCode(stockCode)
                    .stockName(stock.getName())
                    .subscribedAt(LocalDateTime.now())
                    .build();
        }
    }

    public void unsubscribe(String stockCode) {
        User user = getCurrentUser();
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND,
                        "종목을 찾을 수 없습니다: " + stockCode));

        stockSubscriptionRepository.findByUserAndStock(user, stock)
                .ifPresent(subscription -> {
                    stockSubscriptionRepository.delete(subscription);
                    log.info("[StockSubscription] DB 구독 정보 삭제 완료 - User: {}, Stock: {}", user.getId(), stockCode);
                });

        SubscriptionEventDTO event = SubscriptionEventDTO.builder()
                .userId(user.getId())
                .stockCode(stockCode)
                .eventType(SubscriptionEventDTO.EventType.UNSUBSCRIBE)
                .timestamp(LocalDateTime.now())
                .build();

        subscriptionEventProducer.sendSubscriptionEvent(event);
    }

    @Transactional(readOnly = true)
    public List<StockSubscriptionDTO.SubscriptionResponse> getMySubscriptions() {
        User user = getCurrentUser();
        List<StockSubscriptionDTO.SubscriptionResponse> subscriptions = stockSubscriptionRepository.findByUserOrderBySubscribedAtDesc(user)
                .stream()
                .map(StockSubscriptionDTO.SubscriptionResponse::fromEntity)
                .collect(Collectors.toList());
        return subscriptions;
    }

    @Transactional(readOnly = true)
    public long getSubscriberCount(String stockCode) {
        return stockSubscriptionRepository.findByStock_StockCode(stockCode).size();
    }

    /**
     * [수정됨] 모든 활성 종목 코드 목록 조회 (Collector 초기화용)
     * StockSubscription(구독) 테이블과 InterestStock(관심) 테이블의 합집합을 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<String> getAllActiveStockCodes() {
        // 1. 구독 테이블(StockSubscription)에서 조회
        List<String> subscribedCodes = stockSubscriptionRepository.findAllSubscribedStockCodes();

        // 2. 관심 종목 테이블(InterestStock)에서 조회
        List<String> interestCodes = interestStockRepository.findAllInterestStockCodes();

        // 3. 두 리스트를 합치고 중복 제거 (Stream.concat + distinct)
        List<String> activeCodes = Stream.concat(subscribedCodes.stream(), interestCodes.stream())
                .distinct()
                .collect(Collectors.toList());

        log.info("[System] 활성 종목 코드 전체 조회 - Count: {} (Subscribed: {}, Interest: {})",
                activeCodes.size(), subscribedCodes.size(), interestCodes.size());

        return activeCodes;
    }

    private User getCurrentUser() {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "로그인한 사용자를 찾을 수 없습니다."));
    }
}