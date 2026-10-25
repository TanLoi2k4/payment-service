package com.tlcn.payment_service.dto;

import com.tlcn.payment_service.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodBreakdownResponse {
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private long transactionCount;
    private double percentage;
}
