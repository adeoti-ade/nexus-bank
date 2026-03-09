package com.nexus.core.external.nibss;

import java.math.BigDecimal;

record NibssTransferInRequest(
        String nipSessionId,
        BigDecimal amount,
        String senderAccountNumber,
        String senderAccountName,
        String senderBankCode,
        String beneficiaryAccountNumber,
        String narration) {}
