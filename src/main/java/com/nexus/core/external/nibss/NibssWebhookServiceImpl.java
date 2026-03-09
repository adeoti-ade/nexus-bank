package com.nexus.core.external.nibss;

import com.nexus.core.account.AccountService;
import com.nexus.core.common.TransactionStatus;
import com.nexus.core.common.TransactionType;
import com.nexus.core.transaction.Transaction;
import com.nexus.core.transaction.TransactionRepository;
import com.nexus.core.transaction.TransactionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class NibssWebhookServiceImpl implements NibssWebhookService {

    private static final Logger log = LoggerFactory.getLogger(NibssWebhookServiceImpl.class);

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;

    @Override
    @Transactional
    public void processTransferOutCallback(NibssTransferOutCallbackRequest request) {
        Transaction transaction = transactionRepository.findByNipSessionId(request.nipSessionId())
                .orElseThrow(() -> new RuntimeException("Transaction not found for nipSessionId: " + request.nipSessionId()));

        if (transaction.getStatus() != TransactionStatus.PROCESSING) {
            log.info("Transfer-out callback for nipSessionId {} is already in terminal status {}, ignoring",
                    request.nipSessionId(), transaction.getStatus());
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(request.status())) {
            transaction.setStatus(TransactionStatus.COMPLETED);
            log.info("Transaction {} marked COMPLETED via NIBSS callback", request.nipSessionId());
        } else {
            transaction.setStatus(TransactionStatus.FAILED);
            accountService.credit(transaction.getFromAccountNumber(), transaction.getAmount());
            log.info("Transaction {} marked FAILED via NIBSS callback, refunded {} to {}",
                    request.nipSessionId(), transaction.getAmount(), transaction.getFromAccountNumber());
        }

        transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public void processTransferIn(NibssTransferInRequest request) {
        if (transactionRepository.findByNipSessionId(request.nipSessionId()).isPresent()) {
            log.info("Transfer-in for nipSessionId {} already processed, ignoring", request.nipSessionId());
            return;
        }

        accountService.getAccountByNumber(request.beneficiaryAccountNumber());
        accountService.credit(request.beneficiaryAccountNumber(), request.amount());

        Transaction transaction = Transaction.builder()
                .amount(request.amount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .fromAccountNumber(request.senderAccountNumber())
                .toAccountNumber(request.beneficiaryAccountNumber())
                .sourceBankCode(request.senderBankCode())
                .targetAccountName(request.senderAccountName())
                .nipSessionId(request.nipSessionId())
                .description(request.narration())
                .build();

        transactionRepository.save(transaction);
        log.info("Inbound transfer-in processed for nipSessionId {}, credited {} to {}",
                request.nipSessionId(), request.amount(), request.beneficiaryAccountNumber());
    }
}
