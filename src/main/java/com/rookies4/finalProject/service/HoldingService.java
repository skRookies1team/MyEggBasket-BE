package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.dto.HoldingDTO;
import com.rookies4.finalProject.repository.HoldingRepository;
import com.rookies4.finalProject.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class HoldingService {
    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    //1.보유 종목 추가
    public HoldingDTO.HoldingResponse createHolding(HoldingDTO.HoldingRequest request){
        Stock stock = stockRepository.findByTicker(request.getTicker())
                .orElseThrow();
    }
}
