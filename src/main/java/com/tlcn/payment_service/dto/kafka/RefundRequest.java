package com.tlcn.payment_service.dto.kafka;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class RefundRequest {
    private String orderCode;
    private Long orderId;
    private String customerKeycloakId;

    private BigDecimal amount;
    private String currency;

    private String reason;
    private Instant requestedAt;

    public RefundRequest() {
    }
}
