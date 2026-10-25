package com.tlcn.payment_service.service;

import com.tlcn.payment_service.client.VendorServiceClient;
import com.tlcn.payment_service.dto.VendorBankAccountResponse;
import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.enums.PayoutStatus;
import com.tlcn.payment_service.exception.BusinessException;
import com.tlcn.payment_service.exception.ResourceNotFoundException;
import com.tlcn.payment_service.model.PlatformPayableToVendor;
import com.tlcn.payment_service.model.VendorPayout;
import com.tlcn.payment_service.repository.PlatformPayableToVendorRepository;
import com.tlcn.payment_service.repository.VendorPayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorPayoutService {

    private final PlatformPayableToVendorRepository platformPayableToVendorRepository;
    private final VendorPayoutRepository vendorPayoutRepository;
    private final VendorServiceClient vendorServiceClient;

    @Transactional
    public VendorPayout requestPayout(String vendorKeycloakId) {
        if (vendorKeycloakId == null || vendorKeycloakId.isBlank()) {
            throw new BusinessException("Vendor keycloak id is required", HttpStatus.BAD_REQUEST, "PAYOUT_VENDOR_REQUIRED");
        }

        List<VendorPayout> activePayouts = vendorPayoutRepository.findByVendorKeycloakIdAndStatusIn(
            vendorKeycloakId,
            List.of(PayoutStatus.PENDING_APPROVAL, PayoutStatus.APPROVED, PayoutStatus.PROCESSING)
        );
        if (!activePayouts.isEmpty()) {
            throw new BusinessException("Vendor already has a payout in progress", HttpStatus.CONFLICT, "PAYOUT_IN_PROGRESS");
        }

        List<PlatformPayableToVendor> vendorPayables = platformPayableToVendorRepository.findByStatusAndVendorPayoutIsNull(PaymentStatus.PENDING);
        vendorPayables = vendorPayables.stream()
            .filter(payable -> vendorKeycloakId.equals(payable.getVendorKeycloakId()))
            .toList();

        if (vendorPayables.isEmpty()) {
            throw new BusinessException("No payable items found for vendor", HttpStatus.CONFLICT, "PAYOUT_NO_PAYABLE_ITEMS");
        }

        BigDecimal grossAmount = BigDecimal.ZERO;
        for (PlatformPayableToVendor payable : vendorPayables) {
            if (payable != null && payable.getAmount() != null) {
                grossAmount = grossAmount.add(payable.getAmount());
            }
        }

        if (grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                "Payout amount must be greater than zero",
                HttpStatus.CONFLICT,
                "PAYOUT_NO_PAYABLE_ITEMS"
            );
        }

        VendorBankAccountResponse bankAccount = vendorServiceClient.getMyVerifiedBankAccount().getData();
        if (bankAccount == null) {
            throw new BusinessException(
                "Vendor does not have a verified bank account",
                HttpStatus.CONFLICT,
                "PAYOUT_NO_VERIFIED_BANK"
            );
        }

        return createPayout(vendorKeycloakId, vendorPayables, bankAccount, grossAmount);
    }

    // Chạy vào 0h sáng thứ 6 hàng tuần
    @Scheduled(cron = "0 0 0 * * FRI")
    @Transactional
    public void createWeeklyPayouts() {
        log.info("Starting weekly vendor payout creation job.");

        List<PlatformPayableToVendor> payables = platformPayableToVendorRepository.findByStatusAndVendorPayoutIsNull(PaymentStatus.PENDING);

        // Gom theo từng vendor
        Map<String, List<PlatformPayableToVendor>> payablesByVendor = new java.util.LinkedHashMap<>();
        for (PlatformPayableToVendor payable : payables) {
            if (payable == null || payable.getVendorKeycloakId() == null) {
                continue;
            }
            payablesByVendor.computeIfAbsent(payable.getVendorKeycloakId(), key -> new java.util.ArrayList<>()).add(payable);
        }

        for (Map.Entry<String, List<PlatformPayableToVendor>> entry : payablesByVendor.entrySet()) {
            String vendorId = entry.getKey();
            List<PlatformPayableToVendor> vendorPayables = entry.getValue();

            BigDecimal grossAmount = BigDecimal.ZERO;
            for (PlatformPayableToVendor payable : vendorPayables) {
                if (payable != null && payable.getAmount() != null) {
                    grossAmount = grossAmount.add(payable.getAmount());
                }
            }

            if (grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.info("Skipping payout creation for vendor {} because total amount is {}", vendorId, grossAmount);
                continue;
            }

            // Lấy thông tin tài khoản ngân hàng đã xác thực của vendor
            VendorBankAccountResponse bankAccount;
            try {
                bankAccount = vendorServiceClient.getVerifiedBankAccount(vendorId).getData();
                if (bankAccount == null) {
                    log.warn("Skipping payout for vendor {} because they have no verified bank account.", vendorId);
                    continue;
                }
            } catch (Exception e) {
                log.error("Failed to fetch verified bank account for vendor {}. Error: {}", vendorId, e.getMessage());
                continue; // Bỏ qua vendor này và tiếp tục với vendor khác
            }

            VendorPayout payout = createPayout(vendorId, vendorPayables, bankAccount, grossAmount);
            log.info("Created Payout {} for vendor {} with amount {}", payout.getPayoutCode(), vendorId, payout.getNetAmount());
        }
    }

    private VendorPayout createPayout(
        String vendorKeycloakId,
        List<PlatformPayableToVendor> vendorPayables,
        VendorBankAccountResponse bankAccount,
        BigDecimal grossAmount
    ) {
        BigDecimal transactionFee = BigDecimal.ZERO;
        BigDecimal netAmount = grossAmount.subtract(transactionFee);

        VendorPayout payout = VendorPayout.builder()
            .payoutCode("PAYOUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .vendorKeycloakId(vendorKeycloakId)
            .grossAmount(grossAmount)
            .transactionFee(transactionFee)
            .netAmount(netAmount)
            .status(PayoutStatus.PENDING_APPROVAL)
            .bankCode(bankAccount.getBankCode())
            .bankName(bankAccount.getBankName())
            .bankAccountNumber(bankAccount.getAccountNumber())
            .bankAccountName(bankAccount.getAccountName())
            .build();

        vendorPayables.forEach(p -> p.setVendorPayout(payout));
        payout.setPayableItems(vendorPayables);

        return vendorPayoutRepository.save(payout);
    }

    // Dành cho Admin
    @Transactional
    public VendorPayout approvePayout(@NonNull Long payoutId) {
        VendorPayout payout = vendorPayoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));

        if (payout.getStatus() != PayoutStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                "Only payouts pending approval can be approved.",
                HttpStatus.CONFLICT,
                "PAYOUT_NOT_PENDING_APPROVAL"
            );
        }

        payout.setStatus(PayoutStatus.APPROVED);
        payout.setApprovedAt(Instant.now());
        return vendorPayoutRepository.save(payout);
    }

    // Dành cho Admin
    @Transactional
    public VendorPayout rejectPayout(@NonNull Long payoutId, String reason) {
        VendorPayout payout = vendorPayoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));

        if (payout.getStatus() != PayoutStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                "Only payouts pending approval can be rejected.",
                HttpStatus.CONFLICT,
                "PAYOUT_NOT_PENDING_APPROVAL"
            );
        }

        payout.setStatus(PayoutStatus.REJECTED);
        payout.setRejectionReason(reason);
        payout.setRejectedAt(Instant.now());

        // "Giải phóng" các khoản phải trả để chúng có thể được đưa vào đợt thanh toán tiếp theo.
        for (PlatformPayableToVendor item : payout.getPayableItems()) {
            item.setVendorPayout(null);
        }

        return vendorPayoutRepository.save(payout);
    }

    // Dành cho Admin để xử lý các payout đã FAILED
    @Transactional
    public VendorPayout releaseFailedPayoutItems(@NonNull Long payoutId) {
        VendorPayout payout = vendorPayoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));

        if (payout.getStatus() != PayoutStatus.FAILED) {
            throw new BusinessException(
                "Only FAILED payouts can be processed this way.",
                HttpStatus.CONFLICT,
                "PAYOUT_NOT_FAILED"
            );
        }

        log.info("Releasing payable items for failed payout {}", payout.getPayoutCode());

        // "Giải phóng" các khoản phải trả để chúng có thể được đưa vào đợt thanh toán tiếp theo.
        for (PlatformPayableToVendor item : payout.getPayableItems()) {
            item.setVendorPayout(null);
        }
        // Clear the list to break the association from the payout side
        payout.getPayableItems().clear();

        payout.setStatus(PayoutStatus.FAILED_RECONCILED);
        payout.setRejectionReason(payout.getRejectionReason() + " (Items released for next cycle)");
        return vendorPayoutRepository.save(payout);
    }
}