package com.tlcn.payment_service.dto;

import com.tlcn.payment_service.enums.InvoiceStatus;
import com.tlcn.payment_service.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class VendorInvoiceResponse {
    private Long id;
    private String invoiceCode;
    private String vendorKeycloakId;
    private BigDecimal totalAmount;
    private InvoiceStatus status;
    private Instant dueDate;
    private Long paymentId;
    private PaymentStatus paymentStatus;
    private String checkoutUrl;
    private String providerPaymentIntentId;
}