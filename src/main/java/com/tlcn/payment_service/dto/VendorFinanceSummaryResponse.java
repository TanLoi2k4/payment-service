package com.tlcn.payment_service.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class VendorFinanceSummaryResponse {
    /** Sum of pending platform payables not yet assigned to a payout */
    private BigDecimal availableBalance;
    /** Sum of net amounts from completed payouts */
    private BigDecimal totalWithdrawn;
    /** Sum of gross amounts from completed payouts */
    private BigDecimal totalGrossPaidOut;
    /** Sum of unpaid platform fee invoices (PENDING + OVERDUE) */
    private BigDecimal pendingInvoiceAmount;
    private int pendingPayableCount;
    private int pendingInvoiceCount;
    private int activePayoutCount;
    private BigDecimal minimumPayoutAmount;
    private boolean canRequestPayout;
}
