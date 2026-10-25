package com.tlcn.payment_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tlcn.payment_service.enums.WalletTransactionType;
import com.tlcn.payment_service.model.WalletTransaction;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletTransactionResponse {
    private Long transactionId;
    private Long walletId;
    private Long paymentId;
    private Long refundId;
    private WalletTransactionType transactionType;
    private BigDecimal amount;
    private BigDecimal previousBalance;
    private BigDecimal afterBalance;
    private String initiator;
    private String correlationId;
    private String note;
    private Instant createdAt;

    public static WalletTransactionResponse from(WalletTransaction walletTransaction) {
        return WalletTransactionResponse.builder()
            .transactionId(walletTransaction.getId())
            .walletId(walletTransaction.getWallet() != null ? walletTransaction.getWallet().getId() : null)
            .paymentId(walletTransaction.getPayment() != null ? walletTransaction.getPayment().getId() : null)
            .refundId(walletTransaction.getRefund() != null ? walletTransaction.getRefund().getId() : null)
            .transactionType(walletTransaction.getTransactionType())
            .amount(walletTransaction.getAmount())
            .previousBalance(walletTransaction.getPreviousBalance())
            .afterBalance(walletTransaction.getAfterBalance())
            .initiator(walletTransaction.getInitiator())
            .correlationId(walletTransaction.getCorrelationId())
            .note(walletTransaction.getNote())
            .createdAt(walletTransaction.getCreatedAt())
            .build();
    }
}