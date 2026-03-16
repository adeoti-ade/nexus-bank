package com.nexus.core.external.nibss;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

record NibssTransferInRequest(
        @NotBlank String nipSessionId,
        @NotNull @Positive @DecimalMax("10000000.00") BigDecimal amount,
        String senderAccountNumber,
        String senderAccountName,
        String senderBankCode,
        @NotBlank String beneficiaryAccountNumber,
        String narration) {}
