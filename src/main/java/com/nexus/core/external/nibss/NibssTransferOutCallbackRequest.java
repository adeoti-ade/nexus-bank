package com.nexus.core.external.nibss;

import java.math.BigDecimal;

record NibssTransferOutCallbackRequest(
        String nipSessionId,
        String status,
        String responseCode,
        BigDecimal amount,
        String destinationAccountNumber,
        String destinationBankCode) {}
