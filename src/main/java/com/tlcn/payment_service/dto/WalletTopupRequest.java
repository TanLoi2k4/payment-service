package com.tlcn.payment_service.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTopupRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "10000", message = "amount must be at least 10000 VND")
    private BigDecimal amount;
}
