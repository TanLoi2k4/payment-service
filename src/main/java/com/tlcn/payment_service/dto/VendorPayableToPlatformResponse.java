package com.tlcn.payment_service.dto;

import com.tlcn.payment_service.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class VendorPayableToPlatformResponse {
    private Long id;
    private String orderCode;
    private Long orderId;
    private Long subOrderId;
    private String vendorKeycloakId;
    private BigDecimal amount;
    private BigDecimal commissionRate;
    private PaymentStatus status;
    private Instant paidAt;
    private Instant createdAt;
    private Instant updatedAt;
}
