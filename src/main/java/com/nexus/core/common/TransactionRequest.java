package com.nexus.core.common;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransactionRequest(
    @NotNull @Positive @DecimalMax("10000000.00") BigDecimal amount,
    TransactionType type,
    String fromAccountNumber,
    String toAccountNumber,
    @JsonAlias("bankCode") String targetBankCode,
    @JsonAlias("beneficiaryName") String targetAccountName,
    @Size(max = 255, message = "Description must not exceed 255 characters")
    @Pattern(regexp = "^[^<>]*$", message = "Description must not contain HTML tags")
    String description,
    @NotBlank(message = "idempotencyKey is required")
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
             message = "idempotencyKey must be a valid UUID")
    String idempotencyKey
) {}
