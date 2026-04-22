package com.example.batch.config;

import com.example.batch.dto.TransactionDto;
import com.example.batch.partitioner.IdRangePartitioner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.batch.item.support.builder.CompositeItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SettlementBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    private static final int GRID_SIZE = 12;
    private static final int CHUNK_SIZE = 1000;

    @Bean
    public Job settlementJob() {
        return new JobBuilder("settlementJob", jobRepository)
                .start(masterStep())
                .build();
    }

    @Bean
    public Step masterStep() {
        return new StepBuilder("masterStep", jobRepository)
                .partitioner("workerStep", partitioner())
                .step(workerStep())
                .gridSize(GRID_SIZE)
                .taskExecutor(taskExecutor())
                .build();
    }

    @Bean
    public IdRangePartitioner partitioner() {
        return new IdRangePartitioner(jdbcTemplate);
    }

    @Bean
    public Step workerStep() {
        return new StepBuilder("workerStep", jobRepository)
                .<TransactionDto, Map<String, Object>>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader(null, null))
                .processor(processor())
                .writer(compositeWriter())
                .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<TransactionDto> reader(
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        
        log.info("Worker 파티션 실행: ID {} ~ {}", minId, maxId);
        
        return new JdbcCursorItemReaderBuilder<TransactionDto>()
                .name("transactionReader")
                .dataSource(dataSource)
                .sql("SELECT id, user_id, service_type, amount, transaction_date FROM source_transactions " +
                     "WHERE id BETWEEN ? AND ? AND status = 'READY'")
                .queryArguments(minId, maxId)
                .rowMapper((rs, rowNum) -> new TransactionDto(
                        rs.getLong("id"),
                        rs.getString("user_id"),
                        rs.getString("service_type"),
                        rs.getBigDecimal("amount"),
                        rs.getTimestamp("transaction_date").toLocalDateTime()
                ))
                .fetchSize(CHUNK_SIZE)
                .build();
    }

    @Bean
    public ItemProcessor<TransactionDto, Map<String, Object>> processor() {
        return item -> {
            // 정산 로직: 수수료 3% 계산
            BigDecimal totalAmount = item.amount();
            BigDecimal feeAmount = totalAmount.multiply(new BigDecimal("0.03"));
            BigDecimal netAmount = totalAmount.subtract(feeAmount);

            Map<String, Object> values = new HashMap<>();
            values.put("id", item.id());
            values.put("settlement_date", LocalDate.now());
            values.put("user_id", item.userId());
            values.put("service_type", item.serviceType());
            values.put("total_amount", totalAmount);
            values.put("fee_amount", feeAmount);
            values.put("net_amount", netAmount);
            
            return values;
        };
    }

    @Bean
    public CompositeItemWriter<Map<String, Object>> compositeWriter() {
        return new CompositeItemWriterBuilder<Map<String, Object>>()
                .delegates(Arrays.asList(statusUpdateWriter(), resultWriter()))
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<Map<String, Object>> statusUpdateWriter() {
        return new JdbcBatchItemWriterBuilder<Map<String, Object>>()
                .dataSource(dataSource)
                .sql("UPDATE source_transactions SET status = 'COMPLETED' WHERE id = :id AND status = 'READY'")
                .columnMapped()
                .assertUpdates(false)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<Map<String, Object>> resultWriter() {
        return new JdbcBatchItemWriterBuilder<Map<String, Object>>()
                .dataSource(dataSource)
                .sql("INSERT INTO settlement_results (settlement_date, user_id, service_type, total_amount, fee_amount, net_amount) " +
                     "VALUES (:settlement_date, :user_id, :service_type, :total_amount, :fee_amount, :net_amount) " +
                     "ON CONFLICT (settlement_date, user_id, service_type) DO NOTHING")
                .columnMapped()
                .assertUpdates(false)
                .build();
    }

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(GRID_SIZE);
        executor.setMaxPoolSize(GRID_SIZE);
        executor.setQueueCapacity(0); // Zero queue
        executor.setThreadNamePrefix("settl-worker-");
        executor.initialize();
        return executor;
    }
}
