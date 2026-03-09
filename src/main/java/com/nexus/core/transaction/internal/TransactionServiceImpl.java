package com.nexus.core.transaction.internal;

import com.nexus.core.account.AccountService;
import com.nexus.core.common.Bank;
import com.nexus.core.common.DuplicateTransactionException;
import com.nexus.core.transaction.*;
import com.nexus.core.common.TransactionStatus;
import com.nexus.core.common.TransactionType;
import com.nexus.core.common.TransactionRequest;
import com.nexus.core.common.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final ApplicationEventPublisher eventPublisher;
    private static final int DUPLICATE_WINDOW_SECONDS = 30;

    @Override
    @Transactional
    public TransactionResponse transfer(TransactionRequest request) {
        if (request.idempotencyKey() != null) {
            var existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return mapToResponse(existing.get());
            }
        }

        checkSemanticDuplicate(request.fromAccountNumber(), request.toAccountNumber(), request.amount(), TransactionType.TRANSFER);

        if (request.type() != TransactionType.TRANSFER) {
            throw new IllegalArgumentException("Invalid transaction type for transfer");
        }

        // 1. Debit the sender (Always synchronous to lock funds)
        accountService.debit(request.fromAccountNumber(), request.amount());

        boolean isInternal = Bank.NEXUS.getCode().equals(request.targetBankCode());
        TransactionStatus initialStatus = isInternal ? TransactionStatus.COMPLETED : TransactionStatus.PROCESSING;

        if (isInternal) {
            // Internal: Credit immediately
            accountService.credit(request.toAccountNumber(), request.amount());
        }

        // 2. Create Transaction Record
        Transaction transaction = Transaction.builder()
                .amount(request.amount())
                .type(TransactionType.TRANSFER)
                .status(initialStatus)
                .fromAccountNumber(request.fromAccountNumber())
                .toAccountNumber(request.toAccountNumber())
                .targetBankCode(request.targetBankCode())
                .targetAccountName(request.targetAccountName())
                .description(request.description())
                .idempotencyKey(request.idempotencyKey())
                .nipSessionId(isInternal ? null : UUID.randomUUID().toString())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        // 3. If external, publish event for async NIBSS processing
        if (!isInternal) {
            eventPublisher.publishEvent(new TransactionProcessedEvent(
                    saved.getId(),
                    saved.getTargetBankCode(),
                    saved.getNipSessionId()
            ));
        }

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public TransactionResponse deposit(TransactionRequest request) {
        if (request.idempotencyKey() != null) {
            var existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return mapToResponse(existing.get());
            }
        }

        checkSemanticDuplicate(null, request.toAccountNumber(), request.amount(), TransactionType.DEPOSIT);

        if (request.type() != TransactionType.DEPOSIT) {
            throw new IllegalArgumentException("Invalid transaction type for deposit");
        }

        accountService.credit(request.toAccountNumber(), request.amount());

        Transaction transaction = Transaction.builder()
                .amount(request.amount())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .toAccountNumber(request.toAccountNumber())
                .description(request.description())
                .idempotencyKey(request.idempotencyKey())
                .build();

        return mapToResponse(transactionRepository.save(transaction));
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(TransactionRequest request) {
        if (request.idempotencyKey() != null) {
            var existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return mapToResponse(existing.get());
            }
        }

        checkSemanticDuplicate(request.fromAccountNumber(), null, request.amount(), TransactionType.WITHDRAWAL);

        if (request.type() != TransactionType.WITHDRAWAL) {
            throw new IllegalArgumentException("Invalid transaction type for withdrawal");
        }

        accountService.debit(request.fromAccountNumber(), request.amount());

        Transaction transaction = Transaction.builder()
                .amount(request.amount())
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.COMPLETED)
                .fromAccountNumber(request.fromAccountNumber())
                .description(request.description())
                .idempotencyKey(request.idempotencyKey())
                .build();

        return mapToResponse(transactionRepository.save(transaction));
    }

    private void checkSemanticDuplicate(String from, String to, java.math.BigDecimal amount, TransactionType type) {
        LocalDateTime since = LocalDateTime.now().minusSeconds(DUPLICATE_WINDOW_SECONDS);
        List<Transaction> duplicates = transactionRepository.findSemanticDuplicates(from, to, amount, type, since);
        if (!duplicates.isEmpty()) {
            throw new DuplicateTransactionException("A similar transaction was processed recently. Please wait " + DUPLICATE_WINDOW_SECONDS + " seconds.");
        }
    }

    @Override
    public List<TransactionResponse> getAccountTransactions(String accountNumber) {
        return transactionRepository.findByAccountNumber(accountNumber).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public TransactionResponse getTransactionById(UUID id) {
        return transactionRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + id));
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getFromAccountNumber(),
                transaction.getToAccountNumber(),
                transaction.getTargetBankCode(),
                transaction.getTargetAccountName(),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                transaction.getNibssAttempts()
        );
    }
}
