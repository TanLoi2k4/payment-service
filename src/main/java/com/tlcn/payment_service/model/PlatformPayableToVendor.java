package com.tlcn.payment_service.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tlcn.payment_service.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "platform_payable_to_vendor",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_platform_payable_order_sub", columnNames = {"order_code", "sub_order_id"})
    },
    indexes = {
        @Index(name = "idx_platform_payable_to_vendor_order_code", columnList = "order_code"),
        @Index(name = "idx_platform_payable_to_vendor_order_id", columnList = "order_id"),
        @Index(name = "idx_platform_payable_to_vendor_sub_order_id", columnList = "sub_order_id"),
        @Index(name = "idx_platform_payable_to_vendor_vendor_keycloak_id", columnList = "vendor_keycloak_id"),
        @Index(name = "idx_platform_payable_to_vendor_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformPayableToVendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, length = 50)
    private String orderCode;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "sub_order_id")
    private Long subOrderId;

    @Column(name = "vendor_keycloak_id", length = 120)
    private String vendorKeycloakId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_payout_id")
    private VendorPayout vendorPayout;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = PaymentStatus.PENDING;
        }
    }
}
