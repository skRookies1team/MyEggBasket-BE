package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.dto.StockDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StockService {

    private final StockRepository stockRepository;

    //1. 주식 기본 정보 삽입
    @Transactional
    public StockDTO.StockResponse createStock(StockDTO.StockRequest request){
        if(stockRepository.existsByStockCode(request.getStockCode())){
            throw new BusinessException(ErrorCode.STOCK_TICKER_DUPLICATE);
        }
        Stock stock = Stock.builder()
                .stockCode(request.getStockCode())
                .name(request.getName())
                .marketType(request.getMarketType())
                .corpCode(request.getCorpCode())
                .sector(request.getSector())
                .industryCode(request.getIndustryCode())
                .build();
        return StockDTO.StockResponse.fromEntity(stockRepository.save(stock));
    }

    //2. 주식 정보 조회
    public StockDTO.StockResponse readStock(String stockCode){
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND));
        return StockDTO.StockResponse.fromEntity(stock);
    }

    @Transactional(readOnly = true)
    public List<StockDTO.StockResponse> searchStocks(String keyword) {
        return stockRepository.findByNameContainingIgnoreCaseOrStockCodeContaining(keyword, keyword)
                .stream()
                .map(StockDTO.StockResponse::fromEntity)
                .collect(Collectors.toList());
    }

}

