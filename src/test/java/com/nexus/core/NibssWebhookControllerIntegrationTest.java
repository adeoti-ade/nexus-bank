package com.nexus.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.account.Account;
import com.nexus.core.account.AccountRepository;
import com.nexus.core.auth.UserRepository;
import com.nexus.core.auth.dto.RegisterRequest;
import com.nexus.core.common.TransactionStatus;
import com.nexus.core.common.TransactionType;
import com.nexus.core.transaction.Transaction;
import com.nexus.core.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NibssWebhookControllerIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-secret";

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Account testAccount;

    @BeforeEach
    void setUp() throws Exception {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequest request = new RegisterRequest("Webhook", "User", "webhook@test.com", "P@ssw0rd123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Thread.sleep(300);
        testAccount = accountRepository.findAll().get(0);
    }

    // --- Transfer-out callback tests ---

    @Test
    void transferOutSuccessCallbackCompletesTransaction() throws Exception {
        Transaction tx = createProcessingTransaction("sess-out-success");

        String json = """
                {"nipSessionId":"sess-out-success","status":"SUCCESS","responseCode":"00",
                 "amount":500.00,"destinationAccountNumber":"%s","destinationBankCode":"999"}
                """.formatted(testAccount.getAccountNumber());
        byte[] body = json.getBytes();

        mockMvc.perform(post("/api/webhooks/nibss/transfer-out/callback")
                        .header("X-NIBSS-Signature", hmac(body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Transaction updated = transactionRepository.findById(tx.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void transferOutFailedCallbackRefundsSender() throws Exception {
        createProcessingTransaction("sess-out-failed");

        String json = """
                {"nipSessionId":"sess-out-failed","status":"FAILED","responseCode":"96",
                 "amount":500.00,"destinationAccountNumber":"9999999999","destinationBankCode":"999"}
                """;
        byte[] body = json.getBytes();

        mockMvc.perform(post("/api/webhooks/nibss/transfer-out/callback")
                        .header("X-NIBSS-Signature", hmac(body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Transaction updated = transactionRepository.findByNipSessionId("sess-out-failed").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TransactionStatus.FAILED);

        // Sender (testAccount) should be refunded 500.00
        Account refunded = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(refunded.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void transferOutDuplicateCallbackIsIdempotent() throws Exception {
        createProcessingTransaction("sess-out-dup");

        String json = """
                {"nipSessionId":"sess-out-dup","status":"SUCCESS","responseCode":"00",
                 "amount":500.00,"destinationAccountNumber":"9999999999","destinationBankCode":"999"}
                """;
        byte[] body = json.getBytes();
        String sig = hmac(body);

        // First call → COMPLETED
        mockMvc.perform(post("/api/webhooks/nibss/transfer-out/callback")
                        .header("X-NIBSS-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Second call → idempotent, no change
        mockMvc.perform(post("/api/webhooks/nibss/transfer-out/callback")
                        .header("X-NIBSS-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Transaction updated = transactionRepository.findByNipSessionId("sess-out-dup").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void transferOutInvalidSignatureReturns401() throws Exception {
        createProcessingTransaction("sess-out-badsig");

        String json = """
                {"nipSessionId":"sess-out-badsig","status":"SUCCESS","responseCode":"00",
                 "amount":500.00,"destinationAccountNumber":"9999999999","destinationBankCode":"999"}
                """;

        mockMvc.perform(post("/api/webhooks/nibss/transfer-out/callback")
                        .header("X-NIBSS-Signature", "sha256=" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.getBytes()))
                .andExpect(status().isUnauthorized());
    }

    // --- Transfer-in tests ---

    @Test
    void transferInCreditsBeneficiaryAndCreatesRecord() throws Exception {
        String json = """
                {"nipSessionId":"in-sess-1","amount":750.00,
                 "senderAccountNumber":"1234567890","senderAccountName":"John External",
                 "senderBankCode":"044","beneficiaryAccountNumber":"%s",
                 "narration":"Payment for services"}
                """.formatted(testAccount.getAccountNumber());
        byte[] body = json.getBytes();

        mockMvc.perform(post("/api/webhooks/nibss/transfer-in")
                        .header("X-NIBSS-Signature", hmac(body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Account credited = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(credited.getBalance()).isEqualByComparingTo("750.00");

        Transaction saved = transactionRepository.findByNipSessionId("in-sess-1").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(saved.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(saved.getSourceBankCode()).isEqualTo("044");
    }

    @Test
    void transferInDuplicateNipSessionIdIsIdempotent() throws Exception {
        String json = """
                {"nipSessionId":"in-dup-1","amount":100.00,
                 "senderAccountNumber":"1234567890","senderAccountName":"Sender",
                 "senderBankCode":"044","beneficiaryAccountNumber":"%s",
                 "narration":"Test"}
                """.formatted(testAccount.getAccountNumber());
        byte[] body = json.getBytes();
        String sig = hmac(body);

        // First call
        mockMvc.perform(post("/api/webhooks/nibss/transfer-in")
                        .header("X-NIBSS-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Second call — same nipSessionId, should be idempotent
        mockMvc.perform(post("/api/webhooks/nibss/transfer-in")
                        .header("X-NIBSS-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Balance credited only once
        Account account = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void transferInUnknownBeneficiaryReturns404() throws Exception {
        String json = """
                {"nipSessionId":"in-unknown-1","amount":100.00,
                 "senderAccountNumber":"1234567890","senderAccountName":"Sender",
                 "senderBankCode":"044","beneficiaryAccountNumber":"0000000000",
                 "narration":"Test"}
                """;
        byte[] body = json.getBytes();

        mockMvc.perform(post("/api/webhooks/nibss/transfer-in")
                        .header("X-NIBSS-Signature", hmac(body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void transferInInvalidSignatureReturns401() throws Exception {
        String json = """
                {"nipSessionId":"in-badsig-1","amount":100.00,
                 "senderAccountNumber":"1234567890","senderAccountName":"Sender",
                 "senderBankCode":"044","beneficiaryAccountNumber":"%s",
                 "narration":"Test"}
                """.formatted(testAccount.getAccountNumber());

        mockMvc.perform(post("/api/webhooks/nibss/transfer-in")
                        .header("X-NIBSS-Signature", "sha256=" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.getBytes()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookEndpointDoesNotRequireJwtAuth() throws Exception {
        // No Authorization header — must NOT be blocked by JwtAuthenticationFilter
        String json = """
                {"nipSessionId":"no-auth-test","amount":50.00,
                 "senderAccountNumber":"1234567890","senderAccountName":"Sender",
                 "senderBankCode":"044","beneficiaryAccountNumber":"%s",
                 "narration":"No auth test"}
                """.formatted(testAccount.getAccountNumber());
        byte[] body = json.getBytes();

        mockMvc.perform(post("/api/webhooks/nibss/transfer-in")
                        .header("X-NIBSS-Signature", hmac(body))
                        // deliberately no Authorization header
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // --- Helpers ---

    private Transaction createProcessingTransaction(String nipSessionId) {
        Transaction tx = Transaction.builder()
                .amount(new BigDecimal("500.00"))
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PROCESSING)
                .fromAccountNumber(testAccount.getAccountNumber())
                .toAccountNumber("9999999999")
                .targetBankCode("999")
                .targetAccountName("External Recipient")
                .nipSessionId(nipSessionId)
                .description("Inter-bank transfer")
                .build();
        return transactionRepository.save(tx);
    }

    private String hmac(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(), "HmacSHA256"));
        byte[] hmacBytes = mac.doFinal(body);
        return "sha256=" + HexFormat.of().formatHex(hmacBytes);
    }
}
