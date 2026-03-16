package com.nexus.core.account.internal;

import com.nexus.core.account.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.UUID;

@Service
class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    AccountServiceImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional
    public void createAccount(UUID userId) {
        String accountNumber = generateAccountNumber();
        
        Account account = Account.builder()
                .userId(userId)
                .accountNumber(accountNumber)
                .balance(BigDecimal.ZERO)
                .currency("NGN")
                .status(AccountStatus.ACTIVE)
                .build();

        accountRepository.save(account);
    }

    @Override
    public Account getAccountByUserId(UUID userId) {
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Account not found for user: " + userId));
    }

    @Override
    public Account getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void credit(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        Account account = getAccountByNumber(accountNumber);
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void debit(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        Account account = getAccountByNumber(accountNumber);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
    }

    private String generateAccountNumber() {
        String number;
        do {
            StringBuilder sb = new StringBuilder(10);
            for (int i = 0; i < 10; i++) {
                sb.append(random.nextInt(10));
            }
            number = sb.toString();
        } while (accountRepository.existsByAccountNumber(number));
        return number;
    }
}
