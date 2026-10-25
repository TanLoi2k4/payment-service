package com.tlcn.payment_service.service;

import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.enums.PayoutStatus;
import com.tlcn.payment_service.exception.PayoutTransferException;
import com.tlcn.payment_service.model.PlatformPayableToVendor;
import com.tlcn.payment_service.model.VendorPayout;
import com.tlcn.payment_service.repository.VendorPayoutRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.time.Instant;
import java.util.Map;

@Service
@Slf4j
public class SettlementService {

    private final VendorPayoutRepository vendorPayoutRepository;
    private final VnpaySettlementService vnpaySettlementService;
    private final TransactionTemplate transactionTemplate;

    public SettlementService(
        VendorPayoutRepository vendorPayoutRepository,
        VnpaySettlementService vnpaySettlementService,
        PlatformTransactionManager transactionManager
    ) {
        this.vendorPayoutRepository = vendorPayoutRepository;
        this.vnpaySettlementService = vnpaySettlementService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // Chạy 15 phút một lần để xử lý các lệnh đã duyệt
    @Scheduled(fixedRate = 900000)
    public void processApprovedPayouts() {
        log.info("Starting settlement job for approved payouts.");
        List<Long> approvedPayoutIds = vendorPayoutRepository.findIdsByStatus(PayoutStatus.APPROVED);

        if (approvedPayoutIds.isEmpty()) {
            log.info("No approved payouts to process.");
            return;
        }

        for (Long payoutId : approvedPayoutIds) {
            try {
                processClaimedPayout(payoutId);
            } catch (Exception e) {
                log.error("Unexpected error while processing payout id={}: {}", payoutId, e.getMessage(), e);
            }
        }
    }

    /**
     * Claim APPROVED → PROCESSING in a short committed transaction first,
     * then call VNPAY outside that claim so a later failure cannot roll status
     * back to APPROVED and trigger a double transfer.
     */
    private void processClaimedPayout(Long payoutId) {
        VendorPayout claimed = claimApprovedPayout(payoutId);
        if (claimed == null) {
            log.debug("Skip payout id={} — already claimed by another worker", payoutId);
            return;
        }

        log.info("Processing payout {}", claimed.getPayoutCode());
        try {
            vnpaySettlementService.transferToVendor(claimed);
        } catch (PayoutTransferException e) {
            if (e.isTerminal()) {
                log.error("Terminal transfer failure for payout {}: {}", claimed.getPayoutCode(), e.getMessage(), e);
                markFailedAndRelease(payoutId, e.getMessage());
            } else {
                log.error("Retryable transfer failure for payout {}: {}", claimed.getPayoutCode(), e.getMessage(), e);
                markProcessingError(payoutId, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected transfer failure for payout {}: {}", claimed.getPayoutCode(), e.getMessage(), e);
            markProcessingError(payoutId, e.getMessage());
        }
    }

    private VendorPayout claimApprovedPayout(Long payoutId) {
        return transactionTemplate.execute(status -> {
            int updated = vendorPayoutRepository.claimPayoutByStatus(
                payoutId,
                PayoutStatus.APPROVED,
                PayoutStatus.PROCESSING
            );
            if (updated != 1) {
                return null;
            }
            return vendorPayoutRepository.findById(payoutId).orElse(null);
        });
    }

    private void markProcessingError(Long payoutId, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            vendorPayoutRepository.findById(payoutId).ifPresent(payout -> {
                if (payout.getStatus() == PayoutStatus.PROCESSING) {
                    payout.setRejectionReason("Processing error: " + errorMessage);
                    vendorPayoutRepository.save(payout);
                }
            });
        });
    }

    private void markFailedAndRelease(Long payoutId, String failureReason) {
        transactionTemplate.executeWithoutResult(status -> {
            vendorPayoutRepository.findById(payoutId).ifPresent(payout -> {
                if (payout.getStatus() != PayoutStatus.PROCESSING) {
                    return;
                }
                releasePayableItems(payout);
                payout.setStatus(PayoutStatus.FAILED);
                payout.setRejectionReason(failureReason);
                vendorPayoutRepository.save(payout);
                log.info("Marked payout {} as FAILED after terminal transfer rejection", payout.getPayoutCode());
            });
        });
    }

    // Chạy 2 giờ một lần để kiểm tra các giao dịch bị kẹt
    @Scheduled(cron = "0 0 */2 * * *")
    @Transactional
    public void reconcileStuckPayouts() {
        log.info("Starting job to reconcile stuck payouts.");
        Instant threshold = Instant.now().minus(2, ChronoUnit.HOURS);
        
        // Reconcile both PROCESSING and FAILED payouts that have a provider transaction ID (means VNPAY processed them)
        List<VendorPayout> processingPayouts = vendorPayoutRepository.findByStatusAndUpdatedAtBefore(PayoutStatus.PROCESSING, threshold);
        List<VendorPayout> failedWithTxnId = vendorPayoutRepository.findAll().stream()
            .filter(p -> p.getStatus() == PayoutStatus.FAILED && p.getProviderTransactionId() != null && p.getUpdatedAt() != null && p.getUpdatedAt().isBefore(threshold))
            .toList();

        List<VendorPayout> stuckPayouts = new java.util.ArrayList<>(processingPayouts);
        stuckPayouts.addAll(failedWithTxnId);

        if (stuckPayouts.isEmpty()) {
            log.info("No stuck payouts found.");
            return;
        }

        log.info("Found {} stuck payouts to reconcile.", stuckPayouts.size());
        for (VendorPayout payout : stuckPayouts) {
            try {
                log.info("Querying status for stuck payout {}", payout.getPayoutCode());
                Map<String, String> vnpayStatus = vnpaySettlementService.queryTransactionStatus(payout);

                String responseCode = vnpayStatus.get("vnp_ResponseCode");
                String transactionStatus = vnpayStatus.get("vnp_TransactionStatus");

                // Chỉ xử lý nếu truy vấn thành công và có trạng thái giao dịch
                if ("00".equals(responseCode)) {
                    boolean isSuccess = "00".equals(transactionStatus);
                    String failureReason = isSuccess ? null : "Reconciled as FAILED via query. VNPAY status: " + transactionStatus;
                    log.info("Reconciling payout {} with success status: {}", payout.getPayoutCode(), isSuccess);
                    finalizePayout(payout.getPayoutCode(), isSuccess, failureReason);
                } else {
                    log.warn("Could not determine status for payout {} from VNPAY query. Response code: {}", payout.getPayoutCode(), responseCode);
                }
            } catch (Exception e) {
                log.error("Error during reconciliation of payout {}: {}", payout.getPayoutCode(), e.getMessage(), e);
            }
        }
    }

    /**
     * Phương thức này sẽ được gọi bởi Webhook Controller khi VNPAY thông báo kết quả.
     */
    @Transactional
    public void finalizePayout(String payoutCode, boolean isSuccess, String failureReason) {
        VendorPayout payout = vendorPayoutRepository.findByPayoutCode(payoutCode)
                .orElseThrow(() -> new RuntimeException("Payout not found with code: " + payoutCode));

        if (payout.getStatus() != PayoutStatus.PROCESSING) {
            log.warn("Payout {} is not in PROCESSING state. Current state: {}. Ignoring webhook.", payoutCode, payout.getStatus());
            return;
        }

        if (isSuccess) {
            payout.setStatus(PayoutStatus.COMPLETED);
            payout.setCompletedAt(Instant.now());
            // Cập nhật trạng thái các khoản doanh thu liên quan thành ĐÃ TRẢ
            for (PlatformPayableToVendor item : payout.getPayableItems()) {
                item.setStatus(PaymentStatus.COMPLETED);
            }
        } else {
            releasePayableItems(payout);
            payout.setStatus(PayoutStatus.FAILED);
            payout.setRejectionReason(failureReason);
        }
        vendorPayoutRepository.save(payout);
        log.info("Finalized payout {} with status {}", payoutCode, payout.getStatus());
    }

    private void releasePayableItems(VendorPayout payout) {
        if (payout.getPayableItems() == null) {
            return;
        }

        for (PlatformPayableToVendor item : payout.getPayableItems()) {
            if (item != null) {
                item.setVendorPayout(null);
                item.setStatus(PaymentStatus.PENDING);
            }
        }
        payout.getPayableItems().clear();
    }
}
