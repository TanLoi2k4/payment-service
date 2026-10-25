package com.tlcn.payment_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tlcn.payment_service.model.Refund;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundResponse {
    private Long refundId;
    private String orderCode;
    private Long orderId;
    private BigDecimal amount;
    private String currency;
    private String reason;
    private String status;
    private String provider;
    private String providerRefundId;
    private Instant requestedAt;
    private Instant refundedAt;
    private Instant failedAt;

    public static RefundResponse from(Refund refund) {
        if (refund == null) {
            return null;
        }

        return RefundResponse.builder()
            .refundId(refund.getId())
            .orderCode(refund.getOrderCode())
            .orderId(refund.getOrderId())
            .amount(refund.getAmount())
            .currency(refund.getCurrency())
            .reason(refund.getReason())
            .status(refund.getStatus() == null ? null : refund.getStatus().name())
            .provider(refund.getProvider())
            .providerRefundId(refund.getProviderRefundId())
            .requestedAt(refund.getRequestedAt())
            .refundedAt(refund.getRefundedAt())
            .failedAt(refund.getFailedAt())
            .build();
    }
}