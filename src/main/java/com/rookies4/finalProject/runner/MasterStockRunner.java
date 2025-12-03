package com.rookies4.finalProject.runner;

import com.rookies4.finalProject.domain.entity.Stock;
import com.rookies4.finalProject.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MasterStockRunner implements CommandLineRunner {

    private final StockRepository stockRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("=== Master Stock Data Loading Started ===");

        // 1. 업종명(Sector) 매핑 데이터 로드 (KOSDAQ, KOSPI)
        Map<String, String> sectorMap = new HashMap<>();
        loadSectorData(sectorMap, "data_sector_KOSDOQ.csv");
        loadSectorData(sectorMap, "data_sector_KOSPI.csv");

        // 2. 업종코드(IndustryCode) 매핑 데이터 로드
        Map<String, String> industryCodeMap = new HashMap<>();
        loadIndustryCodeData(industryCodeMap, "data_industry_code.csv");

        // 3. 주식 기본 데이터(data_stock.csv) 로드 및 매핑
        Path csvPath = Paths.get("data", "data_stock.csv");

        if (!Files.exists(csvPath)) {
            log.warn("CSV file not found: {}", csvPath);
            return;
        }

        List<Stock> stocks = new ArrayList<>();
        int processedCount = 0;
        int savedCount = 0;
        int skippedCount = 0;

        // 한국 CSV 파일은 보통 EUC-KR 또는 CP949 인코딩 사용
        Charset charset = detectCharset(csvPath);

        try (BufferedReader reader = Files.newBufferedReader(csvPath, charset)) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                // 헤더 라인 스킵
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                // CSV 파싱 (큰따옴표로 둘러싸인 값 처리)
                String[] columns = parseCsvLine(line);

                if (columns.length < 7) {
                    log.warn("Invalid CSV line (columns < 7): {}", line);
                    skippedCount++;
                    continue;
                }

                try {
                    // 컬럼 매핑:
                    // 인덱스 1: 단축코드 (StockCode)
                    // 인덱스 2: 한글 종목약명 (name)
                    // 인덱스 6: 시장구분 (marketType)
                    String stockCode = cleanCsvValue(columns[1]);
                    String name = cleanCsvValue(columns[2]);
                    String marketType = cleanCsvValue(columns[6]);

                    // 매핑된 데이터 가져오기
                    String sector = sectorMap.getOrDefault(stockCode, null);
                    String industryCode = industryCodeMap.getOrDefault(stockCode, null);

                    Stock stock = Stock.builder()
                            .stockCode(stockCode)
                            .name(name)
                            .marketType(marketType)
                            .sector(sector)             // 업종명 설정
                            .industryCode(industryCode) // 업종코드 설정
                            .build();

                    stocks.add(stock);
                    processedCount++;

                    // 배치 사이즈(예: 1000개)마다 저장하여 메모리 관리
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

            // 남은 데이터 저장
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
     * 업종명(Sector) 데이터를 로드하여 Map에 저장합니다.
     */
    private void loadSectorData(Map<String, String> map, String fileName) {
        Path path = Paths.get("data", fileName);
        if (!Files.exists(path)) {
            log.warn("{} not found, skipping sector loading for this file.", fileName);
            return;
        }

        Charset charset = detectCharset(path);
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            String line;
            boolean isFirst = true;
            while ((line = reader.readLine()) != null) {
                if (isFirst) { isFirst = false; continue; }
                String[] cols = parseCsvLine(line);
                // data_sector_*.csv 구조: 0:종목코드, 1:종목명, 2:시장구분, 3:업종명, ...
                if (cols.length > 3) {
                    String code = cleanCsvValue(cols[0]);
                    String sector = cleanCsvValue(cols[3]);
                    map.put(code, sector);
                }
            }
            log.info("Loaded {} sector info from {}", map.size(), fileName);
        } catch (IOException e) {
            log.error("Error reading {}", fileName, e);
        }
    }

    /**
     * 업종코드(IndustryCode) 데이터를 로드하여 Map에 저장합니다.
     */
    private void loadIndustryCodeData(Map<String, String> map, String fileName) {
        Path path = Paths.get("data", fileName);
        if (!Files.exists(path)) {
            log.warn("{} not found, skipping industry code loading.", fileName);
            return;
        }

        Charset charset = detectCharset(path);
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            String line;
            boolean isFirst = true;
            while ((line = reader.readLine()) != null) {
                if (isFirst) { isFirst = false; continue; }
                String[] cols = parseCsvLine(line);
                // data_industry_code.csv 구조: 0:종목코드, ..., 5:업종코드, ...
                if (cols.length > 5) {
                    String code = cleanCsvValue(cols[0]);
                    String indCode = cleanCsvValue(cols[5]);
                    map.put(code, indCode);
                }
            }
            log.info("Loaded {} industry codes from {}", map.size(), fileName);
        } catch (IOException e) {
            log.error("Error reading {}", fileName, e);
        }
    }

    /**
     * CSV 파일의 인코딩을 감지합니다.
     * UTF-8을 먼저 시도하고, 실패하면 EUC-KR/CP949를 시도합니다.
     */
    private Charset detectCharset(Path csvPath) {
        Charset[] charsets = {StandardCharsets.UTF_8, Charset.forName("EUC-KR"), Charset.forName("CP949")};

        for (Charset charset : charsets) {
            try (BufferedReader reader = Files.newBufferedReader(csvPath, charset)) {
                // 처음 몇 줄만 읽어서 테스트
                String line = reader.readLine();
                if (line != null && !line.isEmpty()) {
                    log.info("Using charset: {} for file: {}", charset, csvPath.getFileName());
                    return charset;
                }
            } catch (IOException e) {
                // 다음 인코딩 시도
            }
        }

        log.warn("Could not detect charset, using UTF-8 as default");
        return StandardCharsets.UTF_8;
    }

    /**
     * CSV 라인을 파싱합니다.
     * 큰따옴표로 둘러싸인 값과 일반 값을 모두 처리합니다.
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
        // 마지막 컬럼 추가
        columns.add(current.toString());

        return columns.toArray(new String[0]);
    }

    /**
     * CSV 값에서 큰따옴표를 제거하고 앞뒤 공백을 제거합니다.
     */
    private String cleanCsvValue(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }
}