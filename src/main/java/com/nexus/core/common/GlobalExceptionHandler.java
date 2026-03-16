package com.nexus.core.common;

import com.nexus.core.auth.EmailAlreadyExistsException;
import com.nexus.core.auth.UserNotFoundException;
import com.nexus.core.common.DuplicateTransactionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@Slf4j
// Restrict to our controllers only — prevents intercepting actuator endpoint exceptions (DEF-009)
@RestControllerAdvice(basePackages = "com.nexus.core")
public class GlobalExceptionHandler {

    @ExceptionHandler(WebhookSignatureException.class)
    ProblemDetail handleWebhookSignature(WebhookSignatureException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        detail.setTitle("Invalid Webhook Signature");
        detail.setType(URI.create("https://nexus-bank.com/errors/invalid-signature"));
        return detail;
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    ProblemDetail handleDuplicateTransaction(DuplicateTransactionException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Duplicate Transaction Detected");
        detail.setType(URI.create("https://nexus-bank.com/errors/duplicate-transaction"));
        return detail;
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ProblemDetail handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Email Already Exists");
        detail.setType(URI.create("https://nexus-bank.com/errors/email-already-exists"));
        return detail;
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The requested user was not found");
        detail.setTitle("User Not Found");
        detail.setType(URI.create("https://nexus-bank.com/errors/user-not-found"));
        return detail;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
        detail.setTitle("Forbidden");
        detail.setType(URI.create("https://nexus-bank.com/errors/forbidden"));
        return detail;
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        detail.setTitle("Authentication Failed");
        detail.setType(URI.create("https://nexus-bank.com/errors/bad-credentials"));
        return detail;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Transaction conflict detected. Please retry.");
        detail.setTitle("Transaction Conflict");
        detail.setType(URI.create("https://nexus-bank.com/errors/transaction-conflict"));
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Invalid Argument");
        detail.setType(URI.create("https://nexus-bank.com/errors/invalid-argument"));
        return detail;
    }

    // Handle malformed JSON bodies (DEF-012)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body");
        detail.setTitle("Bad Request");
        detail.setType(URI.create("https://nexus-bank.com/errors/bad-request"));
        return detail;
    }

    @ExceptionHandler(RuntimeException.class)
    ProblemDetail handleRuntime(RuntimeException ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String title = "Server Error";
        String safeDetail = "An unexpected error occurred. Please try again.";

        // Log the full internal message server-side only
        log.error("Runtime exception [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);

        if (ex.getMessage() != null && ex.getMessage().contains("Insufficient balance")) {
            status = HttpStatus.BAD_REQUEST;
            title = "Insufficient Balance";
            safeDetail = "Insufficient funds for this transaction.";
        } else if (ex.getMessage() != null && (ex.getMessage().contains("Account not found") || ex.getMessage().contains("Transaction not found"))) {
            status = HttpStatus.NOT_FOUND;
            title = "Not Found";
            safeDetail = "The requested resource was not found.";
        }

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, safeDetail);
        detail.setTitle(title);
        detail.setType(URI.create("https://nexus-bank.com/errors/runtime-error"));
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        detail.setTitle("Validation Error");
        detail.setType(URI.create("https://nexus-bank.com/errors/validation"));

        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
        detail.setProperty("errors", errors);

        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        detail.setTitle("Internal Server Error");
        detail.setType(URI.create("https://nexus-bank.com/errors/internal-error"));
        return detail;
    }
}
