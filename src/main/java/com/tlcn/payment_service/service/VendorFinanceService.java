package com.tlcn.payment_service.service;

import com.tlcn.payment_service.dto.PlatformPayableToVendorResponse;
import com.tlcn.payment_service.dto.VendorFinanceSummaryResponse;
import com.tlcn.payment_service.enums.InvoiceStatus;
import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.enums.PayoutStatus;
import com.tlcn.payment_service.model.PlatformPayableToVendor;
import com.tlcn.payment_service.model.VendorInvoice;
import com.tlcn.payment_service.model.VendorPayout;
import com.tlcn.payment_service.repository.PlatformPayableToVendorRepository;
import com.tlcn.payment_service.repository.VendorInvoiceRepository;
import com.tlcn.payment_service.repository.VendorPayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VendorFinanceService {

    private final PlatformPayableToVendorRepository platformPayableToVendorRepository;
    private final VendorPayoutRepository vendorPayoutRepository;
    private final VendorInvoiceRepository vendorInvoiceRepository;
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public VendorFinanceSummaryResponse getSummaryForVendor(String vendorKeycloakId) {
        List<PlatformPayableToVendor> pendingPayables = findPendingPayableEntities(vendorKeycloakId);
        BigDecimal availableBalance = sumPayableAmounts(pendingPayables);

        List<VendorPayout> payouts = vendorPayoutRepository.findByVendorKeycloakIdOrderByCreatedAtDesc(vendorKeycloakId);
        BigDecimal totalWithdrawn = BigDecimal.ZERO;
        BigDecimal totalGrossPaidOut = BigDecimal.ZERO;
        int activePayoutCount = 0;

        for (VendorPayout payout : payouts) {
            if (payout.getStatus() == PayoutStatus.COMPLETED) {
                if (payout.getNetAmount() != null) {
                    totalWithdrawn = totalWithdrawn.add(payout.getNetAmount());
                }
                if (payout.getGrossAmount() != null) {
                    totalGrossPaidOut = totalGrossPaidOut.add(payout.getGrossAmount());
                }
            }
            if (isActivePayoutStatus(payout.getStatus())) {
                activePayoutCount++;
            }
        }

        List<VendorInvoice> unpaidInvoices = vendorInvoiceRepository
            .findByVendorKeycloakIdAndStatusOrderByDueDateDesc(vendorKeycloakId, InvoiceStatus.PENDING);
        unpaidInvoices.addAll(
            vendorInvoiceRepository.findByVendorKeycloakIdAndStatusOrderByDueDateDesc(
                vendorKeycloakId, InvoiceStatus.OVERDUE
            )
        );

        BigDecimal pendingInvoiceAmount = unpaidInvoices.stream()
            .map(VendorInvoice::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean canRequestPayout = activePayoutCount == 0
            && !pendingPayables.isEmpty()
            && availableBalance.compareTo(BigDecimal.ZERO) > 0;

        return VendorFinanceSummaryResponse.builder()
            .availableBalance(availableBalance)
            .totalWithdrawn(totalWithdrawn)
            .totalGrossPaidOut(totalGrossPaidOut)
            .pendingInvoiceAmount(pendingInvoiceAmount)
            .pendingPayableCount(pendingPayables.size())
            .pendingInvoiceCount(unpaidInvoices.size())
            .activePayoutCount(activePayoutCount)
            .minimumPayoutAmount(BigDecimal.ZERO)
            .canRequestPayout(canRequestPayout)
            .build();
    }

    @Transactional(readOnly = true)
    public List<PlatformPayableToVendorResponse> getPendingPayablesForVendor(String vendorKeycloakId) {
        return findPendingPayableEntities(vendorKeycloakId).stream()
            .map(paymentService::toPlatformPayableToVendorResponse)
            .toList();
    }

    private List<PlatformPayableToVendor> findPendingPayableEntities(String vendorKeycloakId) {
        return platformPayableToVendorRepository.findByStatusAndVendorPayoutIsNull(PaymentStatus.PENDING).stream()
            .filter(payable -> vendorKeycloakId.equals(payable.getVendorKeycloakId()))
            .toList();
    }

    private BigDecimal sumPayableAmounts(List<PlatformPayableToVendor> payables) {
        BigDecimal total = BigDecimal.ZERO;
        for (PlatformPayableToVendor payable : payables) {
            if (payable.getAmount() != null) {
                total = total.add(payable.getAmount());
            }
        }
        return total;
    }

    private boolean isActivePayoutStatus(PayoutStatus status) {
        return status == PayoutStatus.PENDING_APPROVAL
            || status == PayoutStatus.APPROVED
            || status == PayoutStatus.PROCESSING;
    }
}
