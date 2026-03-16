package com.nexus.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.account.Account;
import com.nexus.core.account.AccountRepository;
import com.nexus.core.auth.UserRepository;
import com.nexus.core.auth.dto.RegisterRequest;
import com.nexus.core.transaction.TransactionRepository;
import com.nexus.core.common.TransactionType;
import com.nexus.core.common.TransactionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionSemanticDeduplicationTests {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    private String token1;
    private Account account1;

    @BeforeEach
    void setUp() throws Exception {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // Register User 1
        RegisterRequest request = new RegisterRequest("Semantic", "User", "semantic@example.com", "P@ssw0rd123");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        
        token1 = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        Thread.sleep(200);
        account1 = accountRepository.findAll().get(0);
    }

    @Test
    void shouldBlockSemanticDuplicateWithinWindow() throws Exception {
        // Seed balance directly (deposit endpoint is now ADMIN-only)
        account1.setBalance(new BigDecimal("1000.00"));
        accountRepository.save(account1);

        TransactionRequest request1 = new TransactionRequest(
                new BigDecimal("100.00"),
                TransactionType.WITHDRAWAL,
                null, null, null, null, "Withdraw 1", UUID.randomUUID().toString()
        );

        TransactionRequest request2 = new TransactionRequest(
                new BigDecimal("100.00"),
                TransactionType.WITHDRAWAL,
                null, null, null, null, "Withdraw 2", UUID.randomUUID().toString() // Different idempotency key!
        );

        // First request - Success
        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        // Second request - Blocked as semantic duplicate within 30s window
        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Transaction Detected"));

        assertThat(accountRepository.findById(account1.getId()).get().getBalance()).isEqualByComparingTo("900.00");
    }

    @Test
    void shouldAllowDifferentAmountWithinWindow() throws Exception {
        // Seed balance directly (deposit endpoint is now ADMIN-only)
        account1.setBalance(new BigDecimal("1000.00"));
        accountRepository.save(account1);

        TransactionRequest request1 = new TransactionRequest(
                new BigDecimal("100.00"),
                TransactionType.WITHDRAWAL,
                null, null, null, null, "Withdraw 1", UUID.randomUUID().toString()
        );

        TransactionRequest request2 = new TransactionRequest(
                new BigDecimal("200.00"),
                TransactionType.WITHDRAWAL,
                null, null, null, null, "Withdraw 2", UUID.randomUUID().toString()
        );

        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk());

        assertThat(accountRepository.findById(account1.getId()).get().getBalance()).isEqualByComparingTo("700.00");
    }
}
