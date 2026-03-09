package com.nexus.core.transaction;

import com.nexus.core.common.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    @Query("SELECT t FROM Transaction t WHERE t.fromAccountNumber = :accountNumber OR t.toAccountNumber = :accountNumber ORDER BY t.createdAt DESC")
    List<Transaction> findByAccountNumber(String accountNumber);

    java.util.Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    java.util.Optional<Transaction> findByNipSessionId(String nipSessionId);

    @Query("""
        SELECT t FROM Transaction t
        WHERE ((:from IS NULL AND t.fromAccountNumber IS NULL) OR t.fromAccountNumber = :from)
        AND ((:to IS NULL AND t.toAccountNumber IS NULL) OR t.toAccountNumber = :to)
        AND t.amount = :amount
        AND t.type = :type
        AND t.status IN (com.nexus.core.common.TransactionStatus.COMPLETED, com.nexus.core.common.TransactionStatus.PENDING)
        AND t.createdAt > :since
    """)
    List<Transaction> findSemanticDuplicates(
        String from,
        String to,
        java.math.BigDecimal amount,
        TransactionType type,
        java.time.LocalDateTime since
    );
}
