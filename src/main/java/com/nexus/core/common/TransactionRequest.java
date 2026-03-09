package com.nexus.core.common;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TransactionRequest(
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    TransactionType type,
    String fromAccountNumber,
    String toAccountNumber,
    String targetBankCode,
    String targetAccountName,
    String description,
    String idempotencyKey
) {}
