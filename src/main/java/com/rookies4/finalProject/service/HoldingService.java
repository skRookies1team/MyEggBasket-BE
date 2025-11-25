package com.rookies4.finalProject.service;

import com.rookies4.finalProject.dto.HoldingDTO;
import com.rookies4.finalProject.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class HoldingService {
    private final HoldingRepository holdingRepository
    //1.보유 종목 생성
    public HoldingDTO.HoldingResponse createHolding(HoldingDTO.HoldingRequest request){

    }
}
