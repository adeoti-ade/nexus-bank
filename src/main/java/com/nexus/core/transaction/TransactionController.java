package com.nexus.core.transaction;

import com.nexus.core.account.Account;
import com.nexus.core.account.AccountService;
import com.nexus.core.auth.User;
import com.nexus.core.common.TransactionType;
import com.nexus.core.common.TransactionRequest;
import com.nexus.core.common.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final AccountService accountService;

    public TransactionController(TransactionService transactionService, AccountService accountService) {
        this.transactionService = transactionService;
        this.accountService = accountService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody TransactionRequest request) {
        Account myAccount = accountService.getAccountByUserId(user.getId());
        TransactionRequest fullRequest = new TransactionRequest(
                request.amount(),
                TransactionType.TRANSFER,
                myAccount.getAccountNumber(),
                request.toAccountNumber(),
                request.targetBankCode(),
                request.targetAccountName(),
                request.description(),
                request.idempotencyKey()
        );
        return ResponseEntity.ok(transactionService.transfer(fullRequest));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody TransactionRequest request) {
        Account myAccount = accountService.getAccountByUserId(user.getId());
        TransactionRequest fullRequest = new TransactionRequest(
                request.amount(),
                TransactionType.DEPOSIT,
                null,
                myAccount.getAccountNumber(),
                null,
                null,
                request.description(),
                request.idempotencyKey()
        );
        return ResponseEntity.ok(transactionService.deposit(fullRequest));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody TransactionRequest request) {
        Account myAccount = accountService.getAccountByUserId(user.getId());
        TransactionRequest fullRequest = new TransactionRequest(
                request.amount(),
                TransactionType.WITHDRAWAL,
                myAccount.getAccountNumber(),
                null,
                null,
                null,
                request.description(),
                request.idempotencyKey()
        );
        return ResponseEntity.ok(transactionService.withdraw(fullRequest));
    }

    @GetMapping("/history")
    public ResponseEntity<List<TransactionResponse>> getHistory(@AuthenticationPrincipal User user) {
        Account myAccount = accountService.getAccountByUserId(user.getId());
        return ResponseEntity.ok(transactionService.getAccountTransactions(myAccount.getAccountNumber()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        TransactionResponse tx = transactionService.getTransactionById(id);
        String myAccountNumber = accountService.getAccountByUserId(user.getId()).getAccountNumber();
        if (!myAccountNumber.equals(tx.fromAccountNumber()) && !myAccountNumber.equals(tx.toAccountNumber())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(tx);
    }
}
