package com.tlcn.payment_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletBalanceResponse {
    private Long walletId;
    private String customerKeycloakId;
    private BigDecimal balance;
    private Long points;
    private boolean walletExists;
    private Instant createdAt;
    private Instant lastUpdatedAt;
}