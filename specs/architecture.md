# 아키텍처 명세서: 초고속 정산 배치 시스템

## 1. 시스템 개요
본 시스템은 대규모 정산 데이터를 고성능으로 처리하기 위해 설계되었습니다. 최적화된 Spring Batch 기술을 사용하여 일일 1,000만 건의 정산 레코드를 정해진 배치 윈도우 내에 처리하는 것을 목표로 합니다.

## 2. 성능 목표 (KPI)
- **대상 수량:** 일일 10,000,000 건
- **실행 시간:** 20분 이내 (500만 건 / 8분 55초 사용자 지표 기반)
- **처리량 (TPS):** 초당 약 8,000 ~ 10,000 건

## 3. 기술 스택 및 선정 이유
- **배치 프레임워크**: Spring Batch (Local Partitioning) - 트랜잭션 일관성과 단일 노드 확장성을 위해 선택.
- **런타임**: Java/Kotlin (JVM) - 견고한 엔터프라이즈 생태계 활용.
- **저장소**: PostgreSQL 또는 MySQL (RDBMS) - ACID 준수 및 멱등성 보장을 위한 유니크 제약 조건 활용 필수.
- **인프라**: AWS m5.2xlarge (8 vCPU, 32GB RAM) 목표.

## 4. 마스터-워커 아키텍처 (파티셔닝)
- **Master Step**: 데이터를 논리적 범위로 분할하고 파티션을 생성하는 역할.
- **Partitioner**: 레코드 건수 또는 ID 분포를 기반으로 데이터 범위를 동적으로 계산.
- **Worker Step**: `ThreadPoolTaskExecutor`를 사용하여 개별 청크를 병렬로 처리하는 독립 스레드.
- **ItemReader**: 오프셋 부하가 없는 스트리밍 처리를 위해 `JdbcCursorItemReader` 사용.
- **ItemWriter**: 벌크 삽입(Bulk Persistence)을 위해 `JdbcBatchItemWriter` 사용.

## 5. 주요 의사결정 사항 (ADR)
- [ADR 0001: 배치 아키텍처 선택](./decisions/0001-batch-architecture-selection.md)
- [ADR 0002: 멱등성 보장 전략](./decisions/0002-idempotency-strategy.md)
