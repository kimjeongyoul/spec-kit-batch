package com.example.generator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementDataGenerator {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = 
        "INSERT INTO source_transactions (user_id, service_type, amount, status, transaction_date) VALUES (?, ?, ?, ?, ?)";

    @Transactional
    public void generateData(int totalRecords, int batchSize) {
        log.info("{} 건의 테스트 데이터 생성을 시작합니다. (Batch Size: {})", totalRecords, batchSize);
        long startTime = System.currentTimeMillis();

        List<Object[]> batchArgs = new ArrayList<>(batchSize);
        String[] serviceTypes = {"FOOD", "SHOPPING", "FASHION", "ELECTRONICS", "BEAUTY"};

        for (int i = 1; i <= totalRecords; i++) {
            batchArgs.add(new Object[]{
                "user_" + (i % 1000000), // 100만 명의 가상 사용자 분산
                serviceTypes[i % serviceTypes.length],
                new BigDecimal("10000.00"),
                "READY",
                LocalDateTime.now().minusDays(i % 30) // 최근 30일 데이터 분산
            });

            if (i % batchSize == 0 || i == totalRecords) {
                jdbcTemplate.batchUpdate(INSERT_SQL, batchArgs);
                batchArgs.clear();
                if (i % 100000 == 0) {
                    log.info("진행률: {} / {} 건 완료...", i, totalRecords);
                }
            }
        }

        long endTime = System.currentTimeMillis();
        log.info("데이터 생성 완료! 소요 시간: {}ms", (endTime - startTime));
    }
}
