package com.rookies4.finalProject.service;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.domain.entity.StockRelation;
import com.rookies4.finalProject.dto.StockRelationDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.StockRelationRepository;
import com.rookies4.finalProject.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StockRelationService {
    private final StockRelationRepository stockRelationRepository;
    private final StockRepository stockRepository;

    //1. StockRelation 생성
    public StockRelationDTO.StockRelationResponse createStockRelation(StockRelationDTO.StockRelationRequest request){
        Stock fromStock = stockRepository.findByStockCode(request.getFromStockCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND));

        Stock toStock = stockRepository.findByStockCode(request.getToStockCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND));

        StockRelation stockRelation = StockRelation.builder()
                .fromStock(fromStock)
                .toStock((toStock))
                .weight(request.getWeight())
                .relationType(request.getRelationType())
                .build();

        return StockRelationDTO.StockRelationResponse.fromEntity(stockRelationRepository.save(stockRelation));
    }

    //2. FromStock 기준 조회
    @Transactional(readOnly = true)
    public List<StockRelationDTO.StockRelationResponse> readFromStockRelation(String stockCode){
        Stock fromStock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND));
        // N+1 문제 해결: Fetch Join으로 fromStock을 함께 조회
        return stockRelationRepository.findByFromStockWithFetch(fromStock)
                .stream()
                .map(StockRelationDTO.StockRelationResponse::fromEntity)
                .collect(Collectors.toList());
    }
    //3. ToStock 기준 조회
    @Transactional(readOnly = true)
    public List<StockRelationDTO.StockRelationResponse> readToStockRelation(String stockCode){
        Stock toStock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKER_NOT_FOUND));
        // N+1 문제 해결: Fetch Join으로 toStock을 함께 조회
        return stockRelationRepository.findByToStockWithFetch(toStock)
                .stream()
                .map(StockRelationDTO.StockRelationResponse::fromEntity)
                .collect(Collectors.toList());
    }

}

