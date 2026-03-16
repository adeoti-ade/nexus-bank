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
class TransactionIdempotencyTests {

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
        RegisterRequest request = new RegisterRequest("Idempo", "User", "idempo@example.com", "P@ssw0rd123");
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
    void shouldBeIdempotentForWithdrawal() throws Exception {
        // Seed balance directly (deposit endpoint is now ADMIN-only)
        account1.setBalance(new BigDecimal("1000.00"));
        accountRepository.save(account1);

        String idempotencyKey = UUID.randomUUID().toString();
        TransactionRequest withdrawRequest = new TransactionRequest(
                new BigDecimal("300.00"),
                TransactionType.WITHDRAWAL,
                null, null, null, null, "Idempotent Withdrawal", idempotencyKey
        );

        // First request
        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(300.00));

        assertThat(accountRepository.findById(account1.getId()).get().getBalance()).isEqualByComparingTo("700.00");

        // Second request with same idempotency key — should return same result, NOT debit again
        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(300.00));

        // Balance should STILL be 700, not 400
        assertThat(accountRepository.findById(account1.getId()).get().getBalance()).isEqualByComparingTo("700.00");
        assertThat(transactionRepository.findAll()).hasSize(1);
    }
}
