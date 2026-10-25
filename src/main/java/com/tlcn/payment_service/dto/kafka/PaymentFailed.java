package com.tlcn.payment_service.dto.kafka;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentFailed {
    private String orderCode;
    private Long orderId;
    private String customerKeycloakId;

    private BigDecimal amount;
    private String currency;

    private String provider;
    private String providerPaymentIntentId;

    private String failureCode;
    private String failureMessage;

    private Instant failedAt;
}
