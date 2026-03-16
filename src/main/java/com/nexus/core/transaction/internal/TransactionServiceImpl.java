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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
            var existing = transactionRepository.findByIdempotencyKeyAndFromAccountNumber(
                    request.idempotencyKey(), request.fromAccountNumber());
            if (existing.isPresent()) {
                return mapToResponse(existing.get());
            }
            if (transactionRepository.findByIdempotencyKey(request.idempotencyKey()).isPresent()) {
                throw new IllegalArgumentException("Idempotency key already used by a different account");
            }
        }

        // Validate required transfer fields before debiting (DEF-002, DEF-004, DEF-005)
        if (request.toAccountNumber() == null || request.toAccountNumber().isBlank()) {
            throw new IllegalArgumentException("toAccountNumber is required for transfers");
        }
        if (request.fromAccountNumber() != null && request.fromAccountNumber().equals(request.toAccountNumber())) {
            throw new IllegalArgumentException("Cannot transfer to your own account");
        }
        boolean isInternal = Bank.NEXUS.getCode().equals(request.targetBankCode());
        if (!isInternal && (request.targetAccountName() == null || request.targetAccountName().isBlank())) {
            throw new IllegalArgumentException("targetAccountName is required for inter-bank transfers");
        }

        checkSemanticDuplicate(request.fromAccountNumber(), request.toAccountNumber(), request.amount(), TransactionType.TRANSFER);

        if (request.type() != TransactionType.TRANSFER) {
            throw new IllegalArgumentException("Invalid transaction type for transfer");
        }

        // 1. Debit the sender (Always synchronous to lock funds)
        try {
            accountService.debit(request.fromAccountNumber(), request.amount());
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new DuplicateTransactionException("Account was modified concurrently. Please retry the transaction.");
        }

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
            var existing = transactionRepository.findByIdempotencyKeyAndToAccountNumber(
                    request.idempotencyKey(), request.toAccountNumber());
            if (existing.isPresent()) {
                return mapToResponse(existing.get());
            }
            if (transactionRepository.findByIdempotencyKey(request.idempotencyKey()).isPresent()) {
                throw new IllegalArgumentException("Idempotency key already used by a different account");
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
            var existing = transactionRepository.findByIdempotencyKeyAndFromAccountNumber(
                    request.idempotencyKey(), request.fromAccountNumber());
            if (existing.isPresent()) {
                return mapToResponse(existing.get());
            }
            if (transactionRepository.findByIdempotencyKey(request.idempotencyKey()).isPresent()) {
                throw new IllegalArgumentException("Idempotency key already used by a different account");
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

    private void checkSemanticDuplicate(String from, String to, BigDecimal amount, TransactionType type) {
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
        // Normalize BigDecimal scale for consistent JSON output (DEF-006)
        BigDecimal normalizedAmount = transaction.getAmount() != null
                ? transaction.getAmount().setScale(2, RoundingMode.HALF_UP)
                : null;
        return new TransactionResponse(
                transaction.getId(),
                normalizedAmount,
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
