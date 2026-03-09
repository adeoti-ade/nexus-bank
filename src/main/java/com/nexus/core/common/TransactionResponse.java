package com.nexus.core.common;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    BigDecimal amount,
    TransactionType type,
    TransactionStatus status,
    String fromAccountNumber,
    String toAccountNumber,
    String targetBankCode,
    String targetAccountName,
    String description,
    LocalDateTime createdAt,
    int nibssAttempts
) {}
