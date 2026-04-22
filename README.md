# Spec-Kit-Batch: 대용량 정산 시스템 prototype

로컬 파티셔닝(Local Partitioning) 아키텍처를 활용하여 **1,000만 건의 트랜잭션을 20분 이내에 처리**하는 것을 목표로 설계된 고성능 Spring Batch 시스템입니다.

## 🚀 주요 성과 (Performance)
실제 스트레스 테스트 결과, 설계 목표를 압도적으로 초과 달성하였습니다.
- **처리량**: 10,000,000 건 (1,000만 건)
- **소요 시간**: **1분 9초 (69.14초)**
- **목표 대비**: 약 17.3배 빠른 성능 기록

## 🏗 핵심 아키텍처 (Architecture)
### 1. 로컬 파티셔닝 (Local Partitioning)
- `IdRangePartitioner`를 통해 데이터베이스의 ID 범위를 균등하게 분할.
- 12개의 멀티 스레드(`ThreadPoolTaskExecutor`)가 각 파티션을 병렬로 독립 처리하여 CPU 자원 극대화.

### 2. 멱등성 3중 방어 전략 (Idempotency)
금융 데이터의 무결성을 위해 배치가 중단 후 재시작되어도 중복 정산이 발생하지 않도록 설계되었습니다.
- **1단계**: DB 복합 유니크 인덱스 제약.
- **2단계**: `ON CONFLICT DO NOTHING` 기반의 Upsert 패턴.
- **3단계**: 원본 트랜잭션 상태 업데이트(`READY` -> `COMPLETED`)를 통한 낙관적 잠금.

## 🛠 기술 스택
- **Framework**: Spring Boot 3.2.5, Spring Batch 5.1.1
- **Database**: PostgreSQL 15
- **Infrastructure**: Docker, HikariCP (Connection Pool: 50)
- **Build Tool**: Gradle 8.7

## 📖 실행 가이드
### 1. 테스트 데이터 생성 (1,000만 건)
```bash
./gradlew bootRun --args='--spring.profiles.active=generate'
```

### 2. 정산 배치 실행
```bash
./gradlew clean bootRun --args='--spring.batch.job.enabled=true run.id=final_test'
```

### 3. 통합 테스트 실행
```bash
./gradlew test
```

## 📊 상세 리포트
상세한 성능 측정 결과는 [specs/test-report.md](./specs/test-report.md)에서 확인하실 수 있습니다.
