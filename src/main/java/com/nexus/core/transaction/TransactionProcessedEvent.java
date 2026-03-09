package com.nexus.core.transaction;

import java.util.UUID;

public record TransactionProcessedEvent(
    UUID transactionId,
    String targetBankCode,
    String nipSessionId
) {}
