package com.nexus.core.external.nibss;

import com.nexus.core.account.Account;
import com.nexus.core.account.AccountService;
import com.nexus.core.auth.User;
import com.nexus.core.auth.UserRepository;
import com.nexus.core.common.Bank;
import com.nexus.core.common.BeneficiaryResponse;
import com.nexus.core.transaction.NibssService;
import com.nexus.core.common.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
class NibssServiceImpl implements NibssService {

    private static final Logger log = LoggerFactory.getLogger(NibssServiceImpl.class);

    private final AccountService accountService;
    private final UserRepository userRepository;
    private final NibssProperties nibssProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    NibssServiceImpl(AccountService accountService,
                     UserRepository userRepository,
                     NibssProperties nibssProperties,
                     ObjectMapper objectMapper) {
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.nibssProperties = nibssProperties;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();

    }

    @Override
    public BeneficiaryResponse resolveAccount(String bankCode, String accountNumber) {
        log.info("Resolving account: {} in bank: {}", accountNumber, bankCode);
        Bank bank = Bank.fromCode(bankCode);

        String accountName;
        if (bank == Bank.NEXUS) {
            Account account = accountService.getAccountByNumber(accountNumber);
            User user = userRepository.findById(account.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found for internal account"));
            accountName = user.getFirstName() + " " + user.getLastName();
        } else {
            accountName = "MOCK " + bank.name() + " USER";
        }

        return new BeneficiaryResponse(
                accountName,
                accountNumber,
                bankCode,
                bank.getName(),
                UUID.randomUUID().toString(),
                LocalDateTime.now()
        );
    }

    @Override
    public void performInterBankTransfer(TransactionRequest request, String nipSessionId) {
        log.info("Sending inter-bank transfer request to NIBSS for session: {}", nipSessionId);

        if (nibssProperties.mockMode()) {
            return;
        }

        NibssTransferSubmission submission = new NibssTransferSubmission(
                nipSessionId,
                request.amount(),
                request.fromAccountNumber(),
                request.toAccountNumber(),
                request.targetBankCode(),
                request.targetAccountName(),
                request.description()
        );
        String bodyJson = objectMapper.writeValueAsString(submission);
        log.info("Sending inter-bank transfer request to NIBSS: {}", bodyJson);

        restTemplate.postForEntity(
                nibssProperties.baseUrl() + "/transfers",
                submission,
                Void.class
        );

        log.info("Transfer submitted to mock-nibss for session: {}", nipSessionId);
    }
}

record NibssTransferSubmission(
        String nipSessionId,
        BigDecimal amount,
        String fromAccountNumber,
        String toAccountNumber,
        String targetBankCode,
        String targetAccountName,
        String description) {}
