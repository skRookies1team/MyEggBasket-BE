package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.StockSubscription;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.StockSubscriptionDTO;
import com.rookies4.finalProject.dto.kafka.SubscriptionEventDTO;
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

import java.time.LocalDateTime;
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
     * 사용자가 종목을 조회하기 시작하거나 관심종목에 추가할 때 호출됩니다.
     * subscription-events 토픽으로 SUBSCRIBE 이벤트를 발행합니다.
     */
    public StockSubscriptionDTO.SubscriptionResponse subscribe(StockSubscriptionDTO.SubscriptionRequest request) {
        User user = getCurrentUser();
        String stockCode = request.getStockCode();
        String type = request.getType(); // "VIEW" or "INTEREST"

        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND,
                        "구독할 종목을 찾을 수 없습니다: " + stockCode));

        StockSubscription saved = null;

        // [타입별 분기 처리]
        if ("VIEW".equalsIgnoreCase(type)) {
            // 1. VIEW(단순 조회): DB 저장 없이 Kafka 이벤트만 발행하여 실시간 데이터 수신 유도
            log.info("[StockSubscription] 상세 페이지 조회(VIEW) 요청 - User: {}, Stock: {}", user.getId(), stockCode);
            // 필요하다면 여기에 '최근 본 종목' 히스토리 저장 로직 추가 가능
        } else {
            // 2. INTEREST(관심 등록): DB에 저장하고 중복 체크 수행
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

        // [공통] Kafka 이벤트 발행 -> Python Stock Collector가 수신
        SubscriptionEventDTO event = SubscriptionEventDTO.builder()
                .userId(user.getId())
                .stockCode(stockCode)
                .eventType(SubscriptionEventDTO.EventType.SUBSCRIBE)
                .subType(type != null ? type : "VIEW") // 기본값 VIEW
                .timestamp(LocalDateTime.now())
                .build();

        subscriptionEventProducer.sendSubscriptionEvent(event);

        // 응답 생성
        if (saved != null) {
            return StockSubscriptionDTO.SubscriptionResponse.fromEntity(saved);
        } else {
            // VIEW 요청은 DB 엔티티가 없으므로 임시 응답 반환
            return StockSubscriptionDTO.SubscriptionResponse.builder()
                    .stockCode(stockCode)
                    .stockName(stock.getName())
                    .subscribedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * 종목 구독 해지 (조회 종료)
     * 사용자가 종목 조회를 종료하거나 관심종목에서 삭제할 때 호출됩니다.
     */
    public void unsubscribe(String stockCode) {
        User user = getCurrentUser();

        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND,
                        "종목을 찾을 수 없습니다: " + stockCode));

        // DB에 구독 정보가 있는지 확인 (관심 종목인 경우 삭제)
        stockSubscriptionRepository.findByUserAndStock(user, stock)
                .ifPresent(subscription -> {
                    stockSubscriptionRepository.delete(subscription);
                    log.info("[StockSubscription] DB 구독 정보 삭제 완료 - User: {}, Stock: {}", user.getId(), stockCode);
                });

        // [공통] Kafka 해지 이벤트 발행 (Python SC 처리용)
        // VIEW 모드였던 경우 DB에는 없지만 SC에서는 수집을 중단해야 할 수 있음 (SC 내부 레퍼런스 카운팅 등)
        SubscriptionEventDTO event = SubscriptionEventDTO.builder()
                .userId(user.getId())
                .stockCode(stockCode)
                .eventType(SubscriptionEventDTO.EventType.UNSUBSCRIBE)
                .timestamp(LocalDateTime.now())
                .build();

        subscriptionEventProducer.sendSubscriptionEvent(event);
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