package com.nexus.core.transaction.internal;

import com.nexus.core.account.AccountService;
import com.nexus.core.common.TransactionStatus;
import com.nexus.core.transaction.Transaction;
import com.nexus.core.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
class TransactionReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(TransactionReconciliationJob.class);

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;

    @Value("${app.transaction.processing-timeout-minutes:30}")
    private int processingTimeoutMinutes;

    @Scheduled(fixedDelayString = "${app.transaction.reconciliation.interval-ms:300000}")
    @Transactional
    public void reconcileStuckTransactions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(processingTimeoutMinutes);
        List<Transaction> stuck = transactionRepository.findStuckProcessingTransactions(threshold);

        if (stuck.isEmpty()) {
            return;
        }

        log.warn("Reconciliation: found {} stuck PROCESSING transaction(s) older than {} minutes", stuck.size(), processingTimeoutMinutes);

        for (Transaction tx : stuck) {
            try {
                tx.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(tx);

                if (tx.getFromAccountNumber() != null) {
                    accountService.credit(tx.getFromAccountNumber(), tx.getAmount());
                    log.warn("Reconciliation: refunded {} to account {} for stuck transaction {}",
                            tx.getAmount(), tx.getFromAccountNumber(), tx.getId());
                }
            } catch (Exception e) {
                log.error("Reconciliation: failed to process stuck transaction {}: {}", tx.getId(), e.getMessage(), e);
            }
        }
    }
}
