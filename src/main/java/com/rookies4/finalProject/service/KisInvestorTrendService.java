package com.rookies4.finalProject.service;

import com.rookies4.finalProject.component.KisApiClient;
import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.dto.KisApiRequest;
import com.rookies4.finalProject.dto.KisInvestorTrendDTO;
import com.rookies4.finalProject.exception.BusinessException;
import com.rookies4.finalProject.exception.ErrorCode;
import com.rookies4.finalProject.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KisInvestorTrendService {

    private final KisApiClient kisApiClient;
    private final StockRepository stockRepository;

    public KisInvestorTrendDTO.InvestorTrendResponse getInvestorTrend(String stockCode, Long userId) {
        KisApiRequest request = KisApiRequest.builder()
                .path("/uapi/domestic-stock/v1/quotations/inquire-investor")
                .trId("FHKST01010900")
                .param("FID_COND_MRKT_DIV_CODE", "J")
                .param("FID_INPUT_ISCD", stockCode)
                .useVirtualServer(false)
                .build();

        KisInvestorTrendDTO.KisApiResponse body = 
            kisApiClient.get(userId, request, KisInvestorTrendDTO.KisApiResponse.class);

        if (body == null || !"0".equals(body.getRtCd()) || body.getOutput() == null || body.getOutput().isEmpty()) {
            String msg = body != null ? body.getMsg1() : "응답이 없습니다.";
            throw new BusinessException(ErrorCode.KIS_API_ERROR, "투자자 동향 조회 실패: " + msg);
        }

            List<KisInvestorTrendDTO.KisOutput> outputList = body.getOutput();
            KisInvestorTrendDTO.KisOutput targetOutput;

            // 장중(16시 이전)이면 전일 데이터(index 1), 장마감 후엔 당일 데이터(index 0)
            LocalTime now = LocalTime.now();
            LocalTime marketCloseTime = LocalTime.of(16, 0);

            if (now.isBefore(marketCloseTime) && outputList.size() > 1) {
                targetOutput = outputList.get(1);  // 전일 데이터
            } else {
                targetOutput = outputList.get(0);  // 당일 데이터
            }

            String stockName = stockRepository.findById(stockCode)
                    .map(Stock::getName)
                    .orElse(targetOutput.getStockName());

            List<KisInvestorTrendDTO.InvestorInfo> investors = new ArrayList<>();

            investors.add(new KisInvestorTrendDTO.InvestorInfo("개인",
                    parseLong(targetOutput.getPersonalNetBuyQty()),
                    parseLong(targetOutput.getPersonalNetBuyAmount()) * 1_000_000));

            investors.add(new KisInvestorTrendDTO.InvestorInfo("외국인",
                    parseLong(targetOutput.getForeignerNetBuyQty()),
                    parseLong(targetOutput.getForeignerNetBuyAmount()) * 1_000_000));

            investors.add(new KisInvestorTrendDTO.InvestorInfo("기관",
                    parseLong(targetOutput.getInstitutionNetBuyQty()),
                    parseLong(targetOutput.getInstitutionNetBuyAmount()) * 1_000_000));

            return KisInvestorTrendDTO.InvestorTrendResponse.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .closePrice(parseLong(targetOutput.getClosePrice()))
                    .changeAmount(parseLong(targetOutput.getChangeAmount()))
                    .changeSign(targetOutput.getChangeSign())
                    .investors(investors)
                    .build();
    }

    public List<KisInvestorTrendDTO.InvestorTrendResponse>
    getMarketInvestorTrend(Long userId) {
        List<String> tickers = List.of(
                "005930", "000660", "207940", "005380", "000270",
                "055550", "105560", "068270", "015760", "028260",
                "032830", "012330", "035420", "006400", "086790",
                "006405", "000810", "010140", "064350", "138040",
                "051910", "010130", "009540", "267260", "066570",
                "066575", "033780", "003550", "003555", "310200"
        );

        return tickers.stream()
                .map(code -> {
                    try {
                        return getInvestorTrend(code, userId);
                    } catch (Exception e) {
                        log.warn("시장 투자자 동향 조회 실패: {}", code, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Long parseLong(Object value) {
        if (value == null) return 0L;
        String str = String.valueOf(value).replaceAll(",", "").trim();
        if (str.isEmpty()) return 0L;
        return Long.parseLong(str);
    }
}