package com.tlcn.payment_service.dto;

import java.math.BigDecimal;

import com.tlcn.payment_service.enums.PaymentType;
import com.tlcn.payment_service.enums.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

    @NotBlank(message = "orderCode is required")
    private String orderCode;

    private Long orderId;

    @NotBlank(message = "customerKeycloakId is required")
    private String customerKeycloakId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;

    private String currency;

    @NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;

    private PaymentType paymentType;
}
