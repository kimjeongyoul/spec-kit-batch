# 설계도: 정산 배치 구현 가이드

## 🎯 목표
로컬 파티셔닝을 활용하여 1,000만 건의 정산 레코드를 처리하는 Spring Batch Job을 구현합니다.

## 🏗 Job 구조 (SettlementJob)

### Step 1: `SettlementMasterStep` (Master)
- **Partitioner**: `RangeIdPartitioner` (원본 트랜잭션 테이블을 ID 범위별로 분할).
- **Grid Size**: 12 (8코어 CPU 기준 초기 타겟).
- **TaskExecutor**: `ThreadPoolTaskExecutor`.

### Step 2: `SettlementWorkerStep` (Worker)
- **ItemReader**: `JdbcCursorItemReader`
  - **SQL**: `SELECT * FROM source_transactions WHERE id BETWEEN :minId AND :maxId AND status = 'READY'`
  - **FetchSize**: 1,000
- **ItemProcessor**: `SettlementProcessor`
  - 로직: 수수료 계산, 세금 공제, 최종 정산 금액 산출.
- **ItemWriter**: `JdbcBatchItemWriter`
  - **SQL**: `INSERT INTO settlement_results (...) ON CONFLICT (date, user_id) DO NOTHING`
  - **BatchSize**: 1,000 (Chunk Size와 일치).

## 🛠 주요 설정 세부사항

### 1. ThreadPoolTaskExecutor 설정
```java
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(12);
    executor.setMaxPoolSize(12);
    executor.setQueueCapacity(0); // 지연 없는 즉시 처리를 위해 큐 0 설정
    executor.setThreadNamePrefix("settlement-worker-");
    executor.initialize();
    return executor;
}
```

### 2. 트랜잭션 매니저
- 격리 수준 (Isolation Level): `READ_COMMITTED`.
- 전파 레벨 (Propagation): `REQUIRED`.

## ✅ 성공 기준
1. 100만 건 스모크 테스트: 2분 이내 완료.
2. 1,000만 건 스트레스 테스트: 20분 이내 완료.
3. 중단 후 재시작 시 `settlement_results` 테이블에 중복 데이터 0건 보장.
