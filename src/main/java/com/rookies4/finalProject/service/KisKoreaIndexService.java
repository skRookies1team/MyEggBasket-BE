package com.rookies4.finalProject.service;

import com.rookies4.finalProject.component.KisApiClient;
import com.rookies4.finalProject.dto.KisApiRequest;
import com.rookies4.finalProject.dto.KisKoreaIndexDTO;
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
public class KisKoreaIndexService {

    private final KisApiClient kisApiClient;

    public KisKoreaIndexDTO.KisKoreaIndexResponse getKoreaIndex(String indexCode, Long userId) {
        KisApiRequest request = KisApiRequest.builder()
                .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                .trId("FHPUP01700000")
                .param("FID_COND_MRKT_DIV_CODE", "J")
                .param("FID_INPUT_ISCD", indexCode)
                .useVirtualServer(false)
                .build();

        KisKoreaIndexDTO.KisKoreaIndexResponse response =
            kisApiClient.get(userId, request, KisKoreaIndexDTO.KisKoreaIndexResponse.class);

        if (response == null || !"0".equals(response.getRtCd())) {
            String msg = response != null ? response.getMsg1() : "응답이 없습니다.";
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "국내 지수 조회 실패: " + msg);
        }

        return response;
    }
}