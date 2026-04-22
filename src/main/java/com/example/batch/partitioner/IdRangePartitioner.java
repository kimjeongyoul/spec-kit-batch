package com.example.batch.partitioner;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class IdRangePartitioner implements Partitioner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        // 원본 테이블의 최소/최대 ID 조회
        Long min = jdbcTemplate.queryForObject("SELECT MIN(id) FROM source_transactions WHERE status = 'READY'", Long.class);
        Long max = jdbcTemplate.queryForObject("SELECT MAX(id) FROM source_transactions WHERE status = 'READY'", Long.class);

        if (min == null || max == null || min.equals(max)) {
            return new HashMap<>();
        }

        Map<String, ExecutionContext> result = new HashMap<>();
        long targetSize = (max - min) / gridSize + 1;

        long start = min;
        long end = start + targetSize - 1;

        for (int i = 0; i < gridSize; i++) {
            ExecutionContext context = new ExecutionContext();
            context.putLong("minId", start);
            context.putLong("maxId", end);
            
            result.put("partition" + i, context);
            
            start += targetSize;
            end += targetSize;
        }

        return result;
    }
}
