package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.InterestStock;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.User;
import com.rookies4.finalProject.dto.InterestStockDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.InterestStockRepository;
import com.rookies4.finalProject.repository.StockRepository;
import com.rookies4.finalProject.repository.UserRepository;
import com.rookies4.finalProject.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InterestStockService {

    private final StockRepository stockRepository;
    private final InterestStockRepository interestStockRepository;
    private final UserRepository userRepository;

    //1. 관심 종목 추가
    public InterestStockDTO.InterestStockResponse addInterestStock(InterestStockDTO.InterestStockRequest request){
        User user = getCurrentUser();

        Stock stock = stockRepository.findById(request.getStockId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND, "관심 종목으로 등록할 주식을 찾을 수 없습니다."));

        if (interestStockRepository.existsByUserAndStock(user, stock)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "이미 관심 종목에 등록된 주식입니다. 다시 확인해 주세요.");
        }
        InterestStock interestStock = InterestStock.builder()
                .user(user)
                .stock(stock)
                .memo(request.getMemo())
                .build();
        InterestStock savedInterestStock = interestStockRepository.save(interestStock);

        return InterestStockDTO.InterestStockResponse.fromEntity(savedInterestStock);
    }

    //2. 관심종목 조회
    @Transactional(readOnly = true)
    public List<InterestStockDTO.InterestStockResponse> showInterestStock(){
        User user = getCurrentUser();

        return interestStockRepository.findByUserOrderByAddedAtDesc(user)
                .stream()
                .map(InterestStockDTO.InterestStockResponse::fromEntity)
                .collect(Collectors.toList());
    }

    //3. 관심종목 삭제
    public void deleteInterestStock(Long interestStockId){
        User user = getCurrentUser();
        InterestStock interestStock = interestStockRepository.findById(interestStockId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,"관심 종목을 찾을 수 없습니다."));

        if (!interestStock.getUser().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "해당 관심 종목에 대한 삭제 권한이 없습니다.");
        }

        interestStockRepository.delete(interestStock);
    }

    private User getCurrentUser() {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "로그인이 필요합니다.");
        }

        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "로그인한 사용자를 찾을 수 없습니다."));
    }
}
