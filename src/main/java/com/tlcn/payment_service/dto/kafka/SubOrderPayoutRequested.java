package com.tlcn.payment_service.dto.kafka;

import com.tlcn.payment_service.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubOrderPayoutRequested {
    private String eventId;
    private String orderCode;
    private Long orderId;
    private Long subOrderId;
    private String vendorKeycloakId;
    private PaymentMethod paymentMethod;
    private String currency;
    private BigDecimal grossAmountBeforeDiscount;
    private BigDecimal commissionFee;
    private BigDecimal platformPayableToVendor;
    private BigDecimal vendorPayableToPlatform;
    private Instant createdAt;
}
