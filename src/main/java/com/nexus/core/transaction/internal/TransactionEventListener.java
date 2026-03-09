package com.nexus.core.transaction.internal;

import com.nexus.core.account.AccountService;
import com.nexus.core.transaction.NibssService;
import com.nexus.core.transaction.Transaction;
import com.nexus.core.transaction.TransactionProcessedEvent;
import com.nexus.core.transaction.TransactionRepository;
import com.nexus.core.common.TransactionStatus;
import com.nexus.core.common.TransactionRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);
    private static final int MAX_NIBSS_ATTEMPTS = 5;

    private final TransactionRepository transactionRepository;
    private final NibssService nibssService;
    private final AccountService accountService;

    @Value("${app.nibss.mock-mode:true}")
    private boolean mockMode;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionProcessed(TransactionProcessedEvent event) {
        log.info("Processing async transaction: {}", event.transactionId());

        Transaction transaction = transactionRepository.findById(event.transactionId())
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + event.transactionId()));

        if (mockMode) {
            try {
                // Simulate NIBSS Latency
                Thread.sleep(2000);

                // Call Mock NIBSS
                nibssService.performInterBankTransfer(
                        new TransactionRequest(
                                transaction.getAmount(),
                                transaction.getType(),
                                transaction.getFromAccountNumber(),
                                transaction.getToAccountNumber(),
                                transaction.getTargetBankCode(),
                                transaction.getTargetAccountName(),
                                transaction.getDescription(),
                                transaction.getIdempotencyKey()
                        ),
                        event.nipSessionId()
                );

                transaction.setStatus(TransactionStatus.COMPLETED);
                log.info("Transaction {} completed successfully via NIBSS", event.transactionId());

            } catch (Exception e) {
                log.error("Transaction {} failed via NIBSS: {}", event.transactionId(), e.getMessage());
                transaction.setStatus(TransactionStatus.FAILED);

                // Refund the sender
                try {
                    accountService.credit(transaction.getFromAccountNumber(), transaction.getAmount());
                    log.info("Refunded amount {} to account {}", transaction.getAmount(), transaction.getFromAccountNumber());
                } catch (Exception refundError) {
                    log.error("CRITICAL: Failed to refund transaction {} to account {}",
                            event.transactionId(), transaction.getFromAccountNumber(), refundError);
                }
            }

            transactionRepository.save(transaction);
        } else {
            // Real mode: submit to NIBSS; webhook callback handles status update
            try {
                nibssService.performInterBankTransfer(
                        new TransactionRequest(
                                transaction.getAmount(),
                                transaction.getType(),
                                transaction.getFromAccountNumber(),
                                transaction.getToAccountNumber(),
                                transaction.getTargetBankCode(),
                                transaction.getTargetAccountName(),
                                transaction.getDescription(),
                                transaction.getIdempotencyKey()
                        ),
                        event.nipSessionId()
                );
                log.info("Transaction {} submitted to NIBSS, awaiting webhook callback", event.transactionId());
            } catch (Exception e) {
                int currentAttempts = transaction.getNibssAttempts() == null ? 0 : transaction.getNibssAttempts();
                transaction.setNibssAttempts(currentAttempts + 1);
                log.warn("NIBSS submission failed for transaction {} (attempt {}/{}): {}",
                        event.transactionId(), transaction.getNibssAttempts(), MAX_NIBSS_ATTEMPTS, e.getMessage());

                if (transaction.getNibssAttempts() >= MAX_NIBSS_ATTEMPTS) {
                    log.error("Transaction {} exhausted all NIBSS submission attempts, marking FAILED",
                            event.transactionId());
                    transaction.setStatus(TransactionStatus.FAILED);
                    transactionRepository.save(transaction);

                    try {
                        accountService.credit(transaction.getFromAccountNumber(), transaction.getAmount());
                        log.info("Refunded {} to account {}", transaction.getAmount(), transaction.getFromAccountNumber());
                    } catch (Exception refundError) {
                        log.error("CRITICAL: Failed to refund transaction {} to account {}",
                                event.transactionId(), transaction.getFromAccountNumber(), refundError);
                    }
                } else {
                    // Save attempt count, then re-throw so Modulith leaves the event
                    // publication uncompleted — republication will retry after the configured interval
                    transactionRepository.save(transaction);
                    throw e;
                }
            }
        }

        // TODO: Notify user via WebSocket
    }
}
