package com.tlcn.payment_service.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.tlcn.payment_service.enums.WalletTransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "wallet_transactions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallet_transactions_correlation_id", columnNames = "correlation_id")
    },
    indexes = {
        @Index(name = "idx_wallet_transactions_wallet_id", columnList = "wallet_id"),
        @Index(name = "idx_wallet_transactions_refund_id", columnList = "refund_id"),
        @Index(name = "idx_wallet_transactions_payment_id", columnList = "payment_id"),
        @Index(name = "idx_wallet_transactions_type", columnList = "transaction_type"),
        @Index(name = "idx_wallet_transactions_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id")
    private Refund refund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private WalletTransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "previous_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal previousBalance;

    @Column(name = "after_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal afterBalance;

    @Column(name = "initiator", length = 50)
    private String initiator;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @Column(name = "note", length = 255)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.amount == null) {
            this.amount = BigDecimal.ZERO;
        }
        if (this.previousBalance == null) {
            this.previousBalance = BigDecimal.ZERO;
        }
        if (this.afterBalance == null) {
            this.afterBalance = BigDecimal.ZERO;
        }
    }
}