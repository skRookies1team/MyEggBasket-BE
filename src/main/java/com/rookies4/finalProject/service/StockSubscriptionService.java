package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.StockSubscription;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.StockSubscriptionDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.repository.StockSubscriptionRepository;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import com.rookies4.finalProject.service.kafka.SubscriptionEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final SubscriptionEventProducer subscriptionEventProducer;

    /**
     * 종목 구독 (조회 시작)
     * 사용자가 종목을 조회하기 시작하면 호출됩니다.
     * subscription-events 토픽으로 SUBSCRIBE 이벤트를 발행합니다.
     */
    public StockSubscriptionDTO.SubscriptionResponse subscribe(StockSubscriptionDTO.SubscriptionRequest request) {
        User user = getCurrentUser();
        
        Stock stock = stockRepository.findByStockCode(request.getStockCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND, 
                        "구독할 종목을 찾을 수 없습니다: " + request.getStockCode()));

        // 이미 구독 중인지 확인
        if (stockSubscriptionRepository.existsByUserAndStock(user, stock)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "이미 구독 중인 종목입니다.");
        }

        // 구독 정보 저장
        StockSubscription subscription = StockSubscription.builder()
                .user(user)
                .stock(stock)
                .stockName(stock.getName())
                .build();
        
        StockSubscription saved = stockSubscriptionRepository.save(subscription);

        // Kafka 이벤트 발행: StockCollector에게 구독 시작 알림
        subscriptionEventProducer.publishSubscribeEvent(user.getId(), stock.getStockCode());
        
        log.info("[StockSubscription] 종목 구독 성공 - UserId: {}, StockCode: {}, StockName: {}", 
                user.getId(), stock.getStockCode(), stock.getName());

        return StockSubscriptionDTO.SubscriptionResponse.fromEntity(saved);
    }

    /**
     * 종목 구독 해지 (조회 종료)
     * 사용자가 종목 조회를 종료하면 호출됩니다.
     * subscription-events 토픽으로 UNSUBSCRIBE 이벤트를 발행합니다.
     */
    public void unsubscribe(String stockCode) {
        User user = getCurrentUser();
        
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND,
                        "종목을 찾을 수 없습니다: " + stockCode));

        StockSubscription subscription = stockSubscriptionRepository.findByUserAndStock(user, stock)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "구독 중이 아닌 종목입니다."));

        // 구독 정보 삭제
        stockSubscriptionRepository.delete(subscription);

        // Kafka 이벤트 발행: StockCollector에게 구독 해지 알림
        // (다른 사용자가 구독 중이면 StockCollector는 계속 수집)
        subscriptionEventProducer.publishUnsubscribeEvent(user.getId(), stockCode);
        
        log.info("[StockSubscription] 종목 구독 해지 성공 - UserId: {}, StockCode: {}", user.getId(), stockCode);
    }

    /**
     * 내 구독 종목 목록 조회
     */
    @Transactional(readOnly = true)
    public List<StockSubscriptionDTO.SubscriptionResponse> getMySubscriptions() {
        User user = getCurrentUser();
        
        List<StockSubscriptionDTO.SubscriptionResponse> subscriptions = stockSubscriptionRepository.findByUserOrderBySubscribedAtDesc(user)
                .stream()
                .map(StockSubscriptionDTO.SubscriptionResponse::fromEntity)
                .collect(Collectors.toList());
        
        log.info("[Subscription] 내 구독 목록 조회 - UserId: {}, Count: {}", user.getId(), subscriptions.size());
        return subscriptions;
    }

    /**
     * 특정 종목의 구독자 수 조회
     */
    @Transactional(readOnly = true)
    public long getSubscriberCount(String stockCode) {
        return stockSubscriptionRepository.findByStock_StockCode(stockCode).size();
    }

    /**
     * 모든 구독 중인 종목 코드 목록 (중복 제거)
     * StockCollector가 수집해야 할 종목 목록
     */
    @Transactional(readOnly = true)
    public List<String> getAllSubscribedStockCodes() {
        return stockSubscriptionRepository.findAllSubscribedStockCodes();
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
