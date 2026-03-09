package com.nexus.core.common;

import java.time.LocalDateTime;

public record BeneficiaryResponse(
    String accountName,
    String accountNumber,
    String bankCode,
    String bankName,
    String nipSessionId,
    LocalDateTime timestamp
) {}
