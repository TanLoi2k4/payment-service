package com.tlcn.payment_service.model;

import com.tlcn.payment_service.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "vendor_payable_to_platform",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_vendor_payable_order_sub", columnNames = {"order_code", "sub_order_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class VendorPayableToPlatform {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderCode;
    private Long orderId;
    private Long subOrderId;
    private String vendorKeycloakId;
    private BigDecimal amount;
    private BigDecimal commissionRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;
    private Instant paidAt;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @ManyToOne
    @JoinColumn(name = "vendor_invoice_id")
    private VendorInvoice vendorInvoice;
}