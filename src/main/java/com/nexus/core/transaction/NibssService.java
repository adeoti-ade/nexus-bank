package com.nexus.core.transaction;

import com.nexus.core.common.BeneficiaryResponse;
import com.nexus.core.common.TransactionRequest;

public interface NibssService {
    BeneficiaryResponse resolveAccount(String bankCode, String accountNumber);
    void performInterBankTransfer(TransactionRequest request, String nipSessionId);
}
