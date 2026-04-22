package com.example.generator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class GeneratorConfig {

    private final SettlementDataGenerator generator;

    @Bean
    @Profile("generate")
    public CommandLineRunner runGenerator() {
        return args -> {
            log.info("=== 테스트 데이터 생성 모드 실행 ===");
            // 실습을 위해 먼저 100만 건만 생성 (이후 1,000만 건으로 확장 가능)
            int targetCount = 10_000_000; 
            int batchSize = 5_000;
            
            generator.generateData(targetCount, batchSize);
            
            log.info("=== 데이터 생성 완료 후 시스템을 종료합니다 ===");
            System.exit(0);
        };
    }
}
