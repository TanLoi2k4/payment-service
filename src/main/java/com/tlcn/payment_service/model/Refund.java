package com.tlcn.payment_service.model;

import com.tlcn.payment_service.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "refunds",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_refunds_idempotency_key", columnNames = "idempotency_key")
    },
    indexes = {
        @Index(name = "idx_refunds_order_code", columnList = "order_code"),
        @Index(name = "idx_refunds_order_id", columnList = "order_id"),
        @Index(name = "idx_refunds_payment_id", columnList = "payment_id"),
        @Index(name = "idx_refunds_provider_refund", columnList = "provider_refund_id"),
        @Index(name = "idx_refunds_idempotency_key", columnList = "idempotency_key"),
        @Index(name = "idx_refunds_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "order_code", nullable = false, length = 50)
    private String orderCode;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "customer_keycloak_id")
    private String customerKeycloakId;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "reason", length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RefundStatus status = RefundStatus.REQUESTED;

    @Column(name = "provider", length = 30)
    private String provider;

    @Column(name = "provider_refund_id", length = 120)
    private String providerRefundId;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = RefundStatus.REQUESTED;
        }
        if (this.currency == null || this.currency.isBlank()) {
            this.currency = "VND";
        }
    }
}
