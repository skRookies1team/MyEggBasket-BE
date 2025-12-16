package com.rookies4.finalProject.service;

import com.rookies4.finalProject.dto.FinancialDataDto;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

@Service
public class FinancialService {

    public List<FinancialDataDto> getFinancialData() {
        List<FinancialDataDto> dataList = new ArrayList<>();
        
        // 파일 경로 (resources 폴더 아래에 있다고 가정)
        ClassPathResource resource = new ClassPathResource("data/integrated_financial_data.csv");

        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                if (isHeader) { // 첫 줄 헤더 건너뛰기
                    isHeader = false;
                    continue;
                }

                // 쉼표로 구분하되, 큰따옴표 안의 쉼표는 무시 (예: "1,503")
                String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                dataList.add(FinancialDataDto.builder()
                        .corpName(cols[0])
                        .stockCode(cols[1])
                        .bsnsYear(cols[2])
                        .reportName(cols[3])
                        .finRevenue(parseLong(cols[4]))
                        .finOpIncome(parseLong(cols[5]))
                        .finNetIncome(parseLong(cols[6]))
                        .finTotalAssets(parseLong(cols[7]))
                        .finTotalEquity(parseLong(cols[8]))
                        .finTotalLiabilities(parseLong(cols[9]))
                        .divDpsCommon(cols[10])
                        .empTotalCount(cols[11].replace("\"", "")) // 따옴표 제거
                        .capitalChangeCount(getInteger(cols[12]))
                        .corpCode(cols[13])
                        .rceptNo(cols[14])
                        .rceptDt(cols[15])
                        .reprtCode(cols[16])
                        .treasuryStockEvent(cols.length > 17 ? cols[17] : "")
                        .build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dataList;
    }

    private Long parseLong(String val) {
        if (val == null || val.trim().isEmpty()) return 0L;
        try {
            return (long) Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private Integer getInteger(String val) {
        if (val == null || val.trim().isEmpty()) return 0;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}