package com.nexus.core.account;

import java.util.UUID;

public interface AccountService {
    void createAccount(UUID userId);
    Account getAccountByUserId(UUID userId);
    Account getAccountByNumber(String accountNumber);
    void credit(String accountNumber, java.math.BigDecimal amount);
    void debit(String accountNumber, java.math.BigDecimal amount);
}
