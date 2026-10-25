package com.tlcn.payment_service.model;

import com.tlcn.payment_service.enums.PayoutStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vendor_payouts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorPayout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String payoutCode;

    @Column(nullable = false)
    private String vendorKeycloakId;

    @Column(nullable = false)
    private BigDecimal grossAmount; // Tổng doanh thu

    private BigDecimal transactionFee; // Phí giao dịch (nếu có)

    @Column(nullable = false)
    private BigDecimal netAmount; // Số tiền thực nhận

    // Thông tin tài khoản ngân hàng của người bán để chi trả
    private String bankCode;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountName;

    // ID giao dịch từ nhà cung cấp dịch vụ chi hộ (VNPAY)
    @Column(length = 120)
    private String providerTransactionId;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status;

    private String rejectionReason;
    private Instant approvedAt;
    private Instant rejectedAt;
    private Instant completedAt;

    @OneToMany(mappedBy = "vendorPayout", cascade = CascadeType.PERSIST)
    @Builder.Default
    private List<PlatformPayableToVendor> payableItems = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}