-- 원본 트랜잭션 테이블
CREATE TABLE IF NOT EXISTS source_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    service_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) DEFAULT 'READY',
    transaction_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 정산 결과 테이블
CREATE TABLE IF NOT EXISTS settlement_results (
    id BIGSERIAL PRIMARY KEY,
    settlement_date DATE NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    service_type VARCHAR(20) NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL,
    fee_amount DECIMAL(19, 2) NOT NULL,
    net_amount DECIMAL(19, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 멱등성 보장을 위한 복합 유니크 인덱스
    UNIQUE (settlement_date, user_id, service_type)
);

-- 성능 최적화를 위한 인덱스
CREATE INDEX IF NOT EXISTS idx_source_status ON source_transactions (status);
CREATE INDEX IF NOT EXISTS idx_source_id_status ON source_transactions (id, status);
