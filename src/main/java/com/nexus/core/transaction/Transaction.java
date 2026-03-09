package com.nexus.core.transaction;

import com.nexus.core.common.TransactionStatus;
import com.nexus.core.common.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(unique = true)
    private String idempotencyKey;

    private String fromAccountNumber;
    private String toAccountNumber;
    
    private String targetBankCode;
    private String targetAccountName;
    private String sourceBankCode;

    @Column(unique = true)
    private String nipSessionId;

    private String description;

    @Builder.Default
    private Integer nibssAttempts = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public Integer getNibssAttempts() {
        return Objects.requireNonNullElse(this.nibssAttempts, 0);
    }
}
