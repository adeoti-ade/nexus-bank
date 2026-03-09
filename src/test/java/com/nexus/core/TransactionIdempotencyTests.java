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
        RegisterRequest request = new RegisterRequest("Idempo", "User", "idempo@example.com", "password123");
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
    void shouldBeIdempotentForDeposit() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        TransactionRequest depositRequest = new TransactionRequest(
                new BigDecimal("500.00"),
                TransactionType.DEPOSIT,
                null, null, null, null, "Idempotent Deposit", idempotencyKey
        );

        // First request
        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(500.00));

        assertThat(accountRepository.findById(account1.getId()).get().getBalance()).isEqualByComparingTo("500.00");

        // Second request with same key
        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(500.00));

        // Balance should STILL be 500, not 1000
        assertThat(accountRepository.findById(account1.getId()).get().getBalance()).isEqualByComparingTo("500.00");
        assertThat(transactionRepository.findAll()).hasSize(1);
    }
}
