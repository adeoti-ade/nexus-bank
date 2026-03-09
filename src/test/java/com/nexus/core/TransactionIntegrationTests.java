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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionIntegrationTests {

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
    private String token2;
    private Account account1;
    private Account account2;

    @BeforeEach
    void setUp() throws Exception {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // Register User 1
        token1 = registerUser("User", "One", "user1@example.com");
        account1 = accountRepository.findAll().get(0);

        // Register User 2
        token2 = registerUser("User", "Two", "user2@example.com");
        account2 = accountRepository.findAll().stream()
                .filter(a -> !a.getId().equals(account1.getId()))
                .findFirst()
                .orElseThrow();
    }

    private String registerUser(String first, String last, String email) throws Exception {
        RegisterRequest request = new RegisterRequest(first, last, email, "password123");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        
        Thread.sleep(200); // Wait for account creation event
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void shouldPerformDepositAndWithdrawal() throws Exception {
        // Deposit
        TransactionRequest depositRequest = new TransactionRequest(
                new BigDecimal("1000.00"),
                TransactionType.DEPOSIT,
                null, null, null, null, "Test Deposit", null
        );

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.type").value("DEPOSIT"));

        Account updatedAccount = accountRepository.findById(account1.getId()).orElseThrow();
        assertThat(updatedAccount.getBalance()).isEqualByComparingTo("1000.00");

        // Withdrawal
        TransactionRequest withdrawRequest = new TransactionRequest(
                new BigDecimal("400.00"),
                TransactionType.WITHDRAWAL,
                null, null, null, null, "Test Withdrawal", null
        );

        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(400.00))
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"));

        updatedAccount = accountRepository.findById(account1.getId()).orElseThrow();
        assertThat(updatedAccount.getBalance()).isEqualByComparingTo("600.00");
    }

    @Test
    void shouldPerformTransferBetweenAccounts() throws Exception {
        // First deposit into account 1
        account1.setBalance(new BigDecimal("2000.00"));
        accountRepository.save(account1);

        // Transfer from account 1 to account 2
        TransactionRequest transferRequest = new TransactionRequest(
                new BigDecimal("500.00"),
                TransactionType.TRANSFER,
                null, account2.getAccountNumber(), "000", "User Two", "Gift", null
        );

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.fromAccountNumber").value(account1.getAccountNumber()))
                .andExpect(jsonPath("$.toAccountNumber").value(account2.getAccountNumber()));

        assertThat(accountRepository.findById(account1.getId()).get().getBalance()).isEqualByComparingTo("1500.00");
        assertThat(accountRepository.findById(account2.getId()).get().getBalance()).isEqualByComparingTo("500.00");
        
        // Check history
        mockMvc.perform(get("/api/transactions/history")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldFailWithdrawalIfInsufficientBalance() throws Exception {
        TransactionRequest withdrawRequest = new TransactionRequest(
                new BigDecimal("100.00"),
                TransactionType.WITHDRAWAL,
                null, null, null, null, "Broke", null
        );

        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Insufficient Balance"));
    }
}
