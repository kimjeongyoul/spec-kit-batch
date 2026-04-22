package com.example.batch.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionDto(
    Long id,
    String userId,
    String serviceType,
    BigDecimal amount,
    LocalDateTime transactionDate
) {}
