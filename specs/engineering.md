# 엔지니어링 표준 명세서

## 🛠 커밋 컨벤션 (명세 기반)
`<type>(<scope>): <spec-id> - <description>`

## 📐 배치 처리 표준 (최적화)
대규모 데이터 처리(정산) 시 성능 저하(O(N²) 문제)를 방지하기 위해 다음 표준을 반드시 준수해야 합니다.

### 1. ItemReader: 스트리밍 필수
- **PagingItemReader 사용 금지**: 10만 건 이상의 데이터셋에서 `skip/offset` 방식은 지양합니다.
- **CursorItemReader 사용 권장**: 일정한 시간 복잡도를 보장하는 스트리밍 방식(`JdbcCursorItemReader` 등)을 사용합니다.

### 2. ItemWriter: 벌크 실행
- **JdbcBatchItemWriter 사용 필수**: 네트워크 왕복 비용을 최소화하기 위해 벌크 삽입/수정 방식을 사용합니다.
- **개별 저장 금지**: 스텝 내부에서 레코드를 하나씩 반복하여 저장하는 행위를 금지합니다.

### 3. ThreadPool 및 Grid Size
- **Grid Size 계산**: `코어 수 * 1.5` (초기 기준).
- **ThreadPoolTaskExecutor**: `corePoolSize`는 `gridSize`와 일치시키고, `queueCapacity`는 `0`으로 설정하여 워커가 즉시 작업을 처리하도록 합니다.

## 🛡️ 운영 안전장치
1. **커넥션 풀 관리**: HikariCP의 `maximumPoolSize`는 스레드 기아 현상을 방지하기 위해 최소 `Grid Size + 5` 이상으로 설정합니다.
2. **관찰 가능성**: 모든 워커 스레드는 추적 가능한 로그를 위해 MDC(Mapped Diagnostic Context)에 `Partition-ID`를 포함해야 합니다.
3. **멱등성 보장**: 모든 Writer는 "Upsert" 로직(`ON CONFLICT` 등)을 사용하거나 삽입 전 기존 정산 결과 존재 여부를 확인해야 합니다.
4. **회복 탄력성**: 외부 API 호출 시 타임아웃(< 3초)을 설정하고 서킷 브레이커(Resilience4j)를 적용합니다.

## ✅ 완료 정의 (DOD)
- [ ] 처리 결과 건수가 목표(예: 1,000만 건)와 일치하는가?
- [ ] 처리 속도가 목표 TPS(예: 1만 TPS)를 충족하는가?
- [ ] DB에 중복된 정산 항목이 존재하지 않는가?
- [ ] 로그가 Partition-ID를 통해 추적 가능한가?
- [ ] `ai-spec verify` 명령을 통과했는가?
