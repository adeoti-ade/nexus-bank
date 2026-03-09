package com.nexus.core.transaction;

import com.nexus.core.common.TransactionRequest;
import com.nexus.core.common.TransactionResponse;
import java.util.List;
import java.util.UUID;

public interface TransactionService {
    TransactionResponse transfer(TransactionRequest request);
    TransactionResponse deposit(TransactionRequest request);
    TransactionResponse withdraw(TransactionRequest request);
    List<TransactionResponse> getAccountTransactions(String accountNumber);
    TransactionResponse getTransactionById(UUID id);
}
