package com.tlcn.payment_service.model;

import com.tlcn.payment_service.enums.PaymentMethod;
import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.enums.PaymentType;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "payments",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_order_code", columnNames = "order_code"),
        @UniqueConstraint(name = "uk_payments_idempotency_key", columnNames = "idempotency_key")
    },
    indexes = {
        @Index(name = "idx_payments_order_code", columnList = "order_code"),
        @Index(name = "idx_payments_order_id", columnList = "order_id"),
        @Index(name = "idx_payments_customer", columnList = "customer_keycloak_id"),
        @Index(name = "idx_payments_provider_intent", columnList = "provider_payment_intent_id"),
        @Index(name = "idx_payments_provider_checkout_session", columnList = "provider_checkout_session_id"),
        @Index(name = "idx_payments_idempotency_key", columnList = "idempotency_key"),
        @Index(name = "idx_payments_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, length = 50)
    private String orderCode;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "customer_keycloak_id", nullable = false)
    private String customerKeycloakId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type")
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "provider", length = 30)
    private String provider;

    @Column(name = "provider_payment_intent_id", length = 120)
    private String providerPaymentIntentId;

    @Column(name = "provider_checkout_session_id", length = 120)
    private String providerCheckoutSessionId;

    @Column(name = "checkout_url", length = 2048)
    private String checkoutUrl;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Refund> refunds = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = PaymentStatus.PENDING;
        }
        if (this.currency == null || this.currency.isBlank()) {
            this.currency = "VND";
        }
    }
}
