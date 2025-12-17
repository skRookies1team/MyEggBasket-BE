package com.rookies4.finalProject.service;

import com.rookies4.finalProject.component.KisApiClient;
import com.rookies4.finalProject.dto.KisApiRequest;
import com.rookies4.finalProject.dto.KisForeignIndexDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KisForeignIndexService {

    private final KisApiClient kisApiClient;

    public KisForeignIndexDTO.KisForeignIndexResponse getForeignIndex(String indexCode, Long userId) {
        KisApiRequest request = KisApiRequest.builder()
                .path("/uapi/overseas-price/v1/quotations/price")
                .trId("HHDFS00000300")
                .param("AUTH", "")
                .param("EXCD", getExchangeCode(indexCode))
                .param("SYMB", indexCode)
                .useVirtualServer(false)
                .build();

        KisForeignIndexDTO.KisForeignIndexResponse response = 
            kisApiClient.get(userId, request, KisForeignIndexDTO.KisForeignIndexResponse.class);

        if (response == null || !"0".equals(response.getRtCd())) {
            String msg = response != null ? response.getMsg1() : "응답이 없습니다.";
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "해외 지수 조회 실패: " + msg);
        }

        return response;
    }

    private String getExchangeCode(String indexCode) {
        // 해외 지수별 거래소 코드 매핑
        switch (indexCode) {
            case "DJI":
            case "NAS":
            case "SPI":
                return "NYS"; // 미국
            case "NII":
                return "TSE"; // 일본
            case "HSI":
                return "HKS"; // 홍콩
            default:
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 해외 지수 코드입니다.");
        }
    }
}