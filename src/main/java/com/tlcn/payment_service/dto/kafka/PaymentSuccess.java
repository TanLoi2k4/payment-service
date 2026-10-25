package com.tlcn.payment_service.dto.kafka;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentSuccess {
    private String orderCode;
    private Long orderId;
    private String customerKeycloakId;

    private BigDecimal amount;
    private String currency;

    private String provider; // e.g., STRIPE
    private String providerPaymentIntentId;

    private Instant paidAt;
}
