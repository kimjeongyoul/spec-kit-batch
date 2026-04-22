package com.example.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
public class SettlementJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE source_transactions, settlement_results, batch_job_execution, batch_job_execution_context, batch_job_execution_params, batch_job_instance, batch_step_execution, batch_step_execution_context RESTART IDENTITY CASCADE;");
    }

    @Test
    @DisplayName("정산 배치 통합 테스트: 10건의 트랜잭션 처리 및 3% 수수료 계산 검증")
    void settlementJobSuccessTest() throws Exception {
        // given: 10건의 트랜잭션 데이터 준비 (금액 10,000원씩)
        for (int i = 1; i <= 10; i++) {
            jdbcTemplate.update(
                "INSERT INTO source_transactions (user_id, service_type, amount, status, transaction_date) " +
                "VALUES (?, ?, ?, ?, NOW())",
                "user_" + i, "FOOD", new BigDecimal("10000.00"), "READY"
            );
        }

        // when: 배치 실행
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("run.id", "test_" + System.currentTimeMillis())
                .toJobParameters();
        
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then: 상태 확인
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 정산 결과 검증 (10건의 각기 다른 유저이므로 10행 생성 예상)
        List<Map<String, Object>> results = jdbcTemplate.queryForList("SELECT * FROM settlement_results");
        assertThat(results).hasSize(10);
        
        // 수수료 및 정산금액 검증 (10,000원 -> 수수료 300원 -> 정산액 9,700원)
        for (Map<String, Object> row : results) {
            assertThat(((BigDecimal)row.get("fee_amount")).compareTo(new BigDecimal("300.00"))).isZero();
            assertThat(((BigDecimal)row.get("net_amount")).compareTo(new BigDecimal("9700.00"))).isZero();
        }

        // 원본 데이터 상태 업데이트 검증
        Integer completedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM source_transactions WHERE status = 'COMPLETED'", Integer.class);
        assertThat(completedCount).isEqualTo(10);
    }

    @Test
    @DisplayName("멱등성 테스트: 이미 처리된(COMPLETED) 데이터는 다시 정산되지 않아야 함")
    void idempotencyTest() throws Exception {
        // given: 1~100번은 COMPLETED, 101~200번은 READY 상태로 준비
        for (int i = 1; i <= 100; i++) {
            jdbcTemplate.update("INSERT INTO source_transactions (user_id, service_type, amount, status, transaction_date) VALUES (?, ?, ?, ?, NOW())", "old_" + i, "FOOD", 10000, "COMPLETED");
        }
        for (int i = 1; i <= 100; i++) {
            jdbcTemplate.update("INSERT INTO source_transactions (user_id, service_type, amount, status, transaction_date) VALUES (?, ?, ?, ?, NOW())", "new_" + i, "FOOD", 10000, "READY");
        }

        // when: 배치 실행
        jobLauncherTestUtils.launchJob(new JobParametersBuilder().addLong("time", System.currentTimeMillis()).toJobParameters());

        // then: READY였던 100건만 정산되어 결과는 총 100건이어야 함
        Integer resultCount = jdbcTemplate.queryForObject("SELECT count(*) FROM settlement_results", Integer.class);
        assertThat(resultCount).isEqualTo(100);
    }
}
