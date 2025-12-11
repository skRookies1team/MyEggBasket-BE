package com.rookies4.finalProject.runner;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MasterStockRunner implements CommandLineRunner {

    private final StockRepository stockRepository;

    // 한국에서 내려받는 CSV는 100% CP949(EUC-KR) 기반
    private static final Charset CSV_CHARSET = Charset.forName("CP949");

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("=== Master Stock Data Loading Started ===");

        // 1. 업종명(Sector) 매핑 데이터 로드
        Map<String, String> sectorMap = new HashMap<>();
        loadSectorData(sectorMap, "data/data_sector_KOSDAQ.csv");
        loadSectorData(sectorMap, "data/data_sector_KOSPI.csv");

        // 2. 업종코드(IndustryCode) 매핑 데이터 로드
        Map<String, String> industryCodeMap = new HashMap<>();
        loadIndustryCodeData(industryCodeMap, "data/data_industry_code.csv");

        // 3. 주식 기본 데이터 로드
        Resource stockResource = new ClassPathResource("data/data_stock.csv");

        if (!stockResource.exists()) {
            log.warn("CSV file not found: data_stock.csv");
            return;
        }

        List<Stock> stocks = new ArrayList<>();
        int processedCount = 0;
        int savedCount = 0;
        int skippedCount = 0;

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(stockResource.getInputStream(), CSV_CHARSET))) {

            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {

                if (isFirstLine) {  // header skip
                    isFirstLine = false;
                    continue;
                }

                String[] columns = parseCsvLine(line);

                if (columns.length < 7) {
                    log.warn("Invalid CSV line (columns < 7): {}", line);
                    skippedCount++;
                    continue;
                }

                try {
                    String stockCode = cleanCsvValue(columns[1]);
                    String name = cleanCsvValue(columns[2]);
                    String marketType = cleanCsvValue(columns[6]);

                    String sector = sectorMap.get(stockCode);
                    String industryCode = industryCodeMap.get(stockCode);

                    Stock stock = Stock.builder()
                            .stockCode(stockCode)
                            .name(name)
                            .marketType(marketType)
                            .sector(sector)
                            .industryCode(industryCode)
                            .build();

                    stocks.add(stock);
                    processedCount++;

                    if (stocks.size() >= 1000) {
                        stockRepository.saveAll(stocks);
                        savedCount += stocks.size();
                        stocks.clear();
                    }

                } catch (Exception e) {
                    log.error("Error parsing line: {}", line, e);
                    skippedCount++;
                }
            }

            if (!stocks.isEmpty()) {
                stockRepository.saveAll(stocks);
                savedCount += stocks.size();
            }

            log.info("Job Finished. Processed: {}, Saved: {}, Skipped: {}", processedCount, savedCount, skippedCount);

        } catch (IOException e) {
            log.error("Failed to read CSV file", e);
        }
    }

    /**
     * 업종명(Sector) 데이터 로드
     */
    private void loadSectorData(Map<String, String> map, String classpathLocation) {
        Resource resource = new ClassPathResource(classpathLocation);

        if (!resource.exists()) {
            log.warn("{} not found, skipping sector loading.", classpathLocation);
            return;
        }

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(resource.getInputStream(), CSV_CHARSET))) {

            String line;
            boolean isFirst = true;

            while ((line = reader.readLine()) != null) {
                if (isFirst) {
                    isFirst = false;
                    continue;
                }

                String[] cols = parseCsvLine(line);
                if (cols.length > 3) {
                    String code = cleanCsvValue(cols[0]);
                    String sector = cleanCsvValue(cols[3]);
                    map.put(code, sector);
                }
            }

            log.info("Loaded {} sector info from {}", map.size(), classpathLocation);

        } catch (IOException e) {
            log.error("Error reading {}", classpathLocation, e);
        }
    }

    /**
     * 업종코드(IndustryCode) 데이터 로드
     */
    private void loadIndustryCodeData(Map<String, String> map, String classpathLocation) {

        Resource resource = new ClassPathResource(classpathLocation);

        if (!resource.exists()) {
            log.warn("{} not found on classpath.", classpathLocation);
            return;
        }

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(resource.getInputStream(), CSV_CHARSET))) {

            String line;
            boolean isFirst = true;

            while ((line = reader.readLine()) != null) {
                if (isFirst) {
                    isFirst = false;
                    continue;
                }

                String[] cols = parseCsvLine(line);
                if (cols.length > 5) {
                    String code = cleanCsvValue(cols[0]);
                    String indCode = cleanCsvValue(cols[5]);
                    map.put(code, indCode);
                }
            }

            log.info("Loaded {} industry codes from {}", map.size(), classpathLocation);

        } catch (IOException e) {
            log.error("Error reading {}", classpathLocation, e);
        }
    }

    /**
     * CSV 파서
     */
    private String[] parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                columns.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        columns.add(current.toString());
        return columns.toArray(new String[0]);
    }

    /**
     * CSV 값 trim & 따옴표 제거
     */
    private String cleanCsvValue(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }
}
