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
                .path("/uapi/overseas-price/v1/quotations/inquire-time-indexchartprice")
                .trId("FHKST03030200")
                .param("FID_COND_MRKT_DIV_CODE", "N")
                .param("FID_INPUT_ISCD", indexCode)
                .param("FID_HOUR_CLS_CODE", "0")
                .param("FID_PW_DATA_INCU_YN", "Y")
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
}