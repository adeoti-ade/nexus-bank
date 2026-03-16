package com.nexus.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.account.Account;
import com.nexus.core.account.AccountRepository;
import com.nexus.core.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldCreateAccountOnUserRegistration() throws Exception {
        // Register a user
        RegisterRequest registerRequest = new RegisterRequest(
                "John",
                "Doe",
                "john.doe@example.com",
                "P@ssw0rd123"
        );

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).get("accessToken").asText();

        // Wait a bit for the event listener to complete if it's async
        // In this setup, let's check the database
        // Actually, @ApplicationModuleListener might be slightly delayed depending on config
        // But for this project, let's see if it works synchronously first
        
        Thread.sleep(500); // Give it a moment to process the event

        // Check if account was created
        // We'll need to query by email or find all for simplicity in this test
        // Since it's a fresh test, find all should work
        assertThat(accountRepository.findAll()).hasSize(1);
        Account account = accountRepository.findAll().get(0);
        assertThat(account.getAccountNumber()).hasSize(10);
        assertThat(account.getBalance()).isZero();

        // Check the /api/accounts/me endpoint
        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(account.getAccountNumber()))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("NGN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
