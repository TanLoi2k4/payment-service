package com.tlcn.payment_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tlcn.payment_service.enums.PaymentMethod;
import com.tlcn.payment_service.enums.PaymentStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentDetailResponse {
    private Long paymentId;
    private String orderCode;
    private Long orderId;
    private String customerKeycloakId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String provider;
    private String providerPaymentIntentId;
    private String providerCheckoutSessionId;
    private String checkoutUrl;
    private String failureCode;
    private String failureMessage;
    private Instant requestedAt;
    private Instant paidAt;
    private Instant failedAt;
    private Instant cancelledAt;
    private Instant createdAt;
    private Instant updatedAt;
}