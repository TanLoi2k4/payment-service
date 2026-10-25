package com.tlcn.payment_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tlcn.payment_service.dto.RefundRequest;
import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.enums.RefundStatus;
import com.tlcn.payment_service.enums.WalletTransactionType;
import com.tlcn.payment_service.exception.BusinessException;
import com.tlcn.payment_service.exception.ResourceNotFoundException;
import com.tlcn.payment_service.model.Payment;
import com.tlcn.payment_service.model.Refund;
import com.tlcn.payment_service.model.Wallet;
import com.tlcn.payment_service.model.WalletTransaction;
import com.tlcn.payment_service.repository.PaymentRepository;
import com.tlcn.payment_service.repository.RefundRepository;
import com.tlcn.payment_service.repository.WalletRepository;
import com.tlcn.payment_service.repository.WalletTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private static final String PROVIDER_WALLET = "WALLET";
    private static final String INITIATOR_SYSTEM = "SYSTEM";

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public Refund requestRefund(RefundRequest request, String idempotencyKey) {
        Objects.requireNonNull(request, "request");

        String effectiveIdempotencyKey = normalizeIdempotencyKey(idempotencyKey, request.getOrderCode());
        Optional<Refund> existingRefund = refundRepository.findByIdempotencyKey(effectiveIdempotencyKey);
        if (existingRefund.isPresent()) {
            Refund refund = existingRefund.get();
            if (!Objects.equals(refund.getOrderCode(), request.getOrderCode())
                || !normalizeMoney(refund.getAmount()).equals(normalizeMoney(request.getAmount()))) {
                throw new BusinessException(
                    "Idempotency key already used for a different refund",
                    HttpStatus.CONFLICT,
                    "REFUND_IDEMPOTENCY_CONFLICT"
                );
            }
            return refund;
        }
        return processRefund(request, effectiveIdempotencyKey);
    }

    private Refund processRefund(RefundRequest request, String effectiveIdempotencyKey) {
        validateRequest(request);

        // Sử dụng khóa bi quan để ngăn chặn các cập nhật đồng thời vào payment
        Payment payment = paymentRepository.findByOrderCodeWithLock(request.getOrderCode())
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for orderCode=" + request.getOrderCode()));

        String customerKeycloakId = resolveCustomerKeycloakId(request, payment);
        BigDecimal refundAmount = normalizeMoney(request.getAmount());
        BigDecimal refundableRemaining = calculateRefundableRemaining(payment.getOrderCode(), payment.getAmount());

        if (refundAmount.compareTo(refundableRemaining) > 0) {
            throw new BusinessException(
                "Refund amount exceeds refundable balance",
                HttpStatus.CONFLICT,
                "REFUND_AMOUNT_EXCEEDED"
            );
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new BusinessException(
                "Payment is not eligible for refund from status=" + payment.getStatus(),
                HttpStatus.CONFLICT,
                "PAYMENT_NOT_REFUNDABLE"
            );
        }

        // Sử dụng khóa bi quan để đảm bảo tính toàn vẹn của số dư ví
        Wallet wallet = getOrCreateWalletWithLock(customerKeycloakId);
        BigDecimal previousBalance = normalizeMoney(wallet.getBalance());
        BigDecimal afterBalance = previousBalance.add(refundAmount).setScale(2, RoundingMode.HALF_UP);

        wallet.setBalance(afterBalance);
        wallet = walletRepository.save(wallet);

        Instant now = Instant.now();
        Refund refund = Refund.builder()
            .payment(payment)
            .orderCode(payment.getOrderCode())
            .orderId(payment.getOrderId())
            .customerKeycloakId(customerKeycloakId)
            .idempotencyKey(effectiveIdempotencyKey)
            .amount(refundAmount)
            .currency(resolveCurrency(request.getCurrency(), payment.getCurrency()))
            .reason(request.getReason())
            .status(RefundStatus.SUCCEEDED)
            .provider(PROVIDER_WALLET)
            .providerRefundId(null)
            .requestedAt(now)
            .refundedAt(now)
            .build();
        refund = refundRepository.save(refund);

        WalletTransaction transaction = WalletTransaction.builder()
            .wallet(wallet)
            .refund(refund)
            .payment(payment)
            .transactionType(WalletTransactionType.REFUND_CREDIT)
            .amount(refundAmount)
            .previousBalance(previousBalance)
            .afterBalance(afterBalance)
            .initiator(INITIATOR_SYSTEM)
            .correlationId(effectiveIdempotencyKey)
            .note(buildTransactionNote(payment.getOrderCode(), request.getReason()))
            .build();
        walletTransactionRepository.save(transaction);

        BigDecimal refundedTotalAfter = calculateRefundedTotal(payment.getOrderCode());        
        updatePaymentRefundStatus(payment, refundedTotalAfter);
        paymentRepository.save(payment);

        Refund savedRefund = refund;
        log.info("Refund completed for orderCode={}, refundId={}, amount={}", savedRefund.getOrderCode(), savedRefund.getId(), savedRefund.getAmount());
        return savedRefund;
    }

    private void updatePaymentRefundStatus(Payment payment, BigDecimal refundedTotalAfter) {
        BigDecimal paymentAmount = normalizeMoney(payment.getAmount());
        if (refundedTotalAfter.compareTo(paymentAmount) >= 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
            return;
        }
        payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
    }

    private BigDecimal calculateRefundableRemaining(String orderCode, BigDecimal paymentAmount) {
        BigDecimal refundedTotal = calculateRefundedTotal(orderCode);
        BigDecimal remaining = normalizeMoney(paymentAmount).subtract(refundedTotal).setScale(2, RoundingMode.HALF_UP);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return remaining;
    }

    private BigDecimal calculateRefundedTotal(String orderCode) {
        List<Refund> refunds = refundRepository.findByOrderCodeOrderByCreatedAtDesc(orderCode);
        return refunds.stream()
            .filter(refund -> refund.getStatus() == RefundStatus.SUCCEEDED)
            .map(Refund::getAmount)
            .filter(Objects::nonNull)
            .map(this::normalizeMoney)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private Wallet getOrCreateWalletWithLock(String customerKeycloakId) {
        Optional<Wallet> existing = walletRepository.findByCustomerKeycloakIdWithLock(customerKeycloakId);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            walletRepository.saveAndFlush(Wallet.builder()
                .customerKeycloakId(customerKeycloakId)
                .balance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .points(0L)
                .build());
        } catch (DataIntegrityViolationException duplicate) {
            log.info("Concurrent wallet create detected for customerKeycloakId={}", customerKeycloakId);
        }

        return walletRepository.findByCustomerKeycloakIdWithLock(customerKeycloakId)
            .orElseThrow(() -> new BusinessException(
                "Wallet not found after create for customer",
                HttpStatus.CONFLICT,
                "WALLET_CREATE_RACE"
            ));
    }

    private Wallet getOrCreateWallet(String customerKeycloakId) {
        return getOrCreateWalletWithLock(customerKeycloakId);
    }

    private void validateRequest(RefundRequest request) {
        if (request.getOrderCode() == null || request.getOrderCode().isBlank()) {
            throw new BusinessException("orderCode is required", HttpStatus.BAD_REQUEST, "REFUND_ORDER_CODE_REQUIRED");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("amount must be greater than 0", HttpStatus.BAD_REQUEST, "REFUND_AMOUNT_INVALID");
        }
    }

    private String resolveCustomerKeycloakId(RefundRequest request, Payment payment) {
        if (request.getCustomerKeycloakId() == null || request.getCustomerKeycloakId().isBlank()) {
            return payment.getCustomerKeycloakId();
        }

        if (!Objects.equals(payment.getCustomerKeycloakId(), request.getCustomerKeycloakId())) {
            throw new BusinessException("Customer does not match payment owner", HttpStatus.FORBIDDEN, "REFUND_CUSTOMER_MISMATCH");
        }

        return request.getCustomerKeycloakId();
    }

    private String normalizeIdempotencyKey(String idempotencyKey, String fallbackOrderCode) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey.trim();
        }

        // Generate UUID-based key to allow multiple refunds per order with different amounts
        return "refund:" + fallbackOrderCode + ":" + java.util.UUID.randomUUID().toString();
    }

    private String resolveCurrency(String requestedCurrency, String fallbackCurrency) {
        String currency = requestedCurrency;
        if (currency == null || currency.isBlank()) {
            currency = fallbackCurrency;
        }

        if (currency == null || currency.isBlank()) {
            return "vnd";
        }

        String normalized = currency.trim().toLowerCase();
        return switch (normalized) {
            case "vnd", "vnđ" -> "vnd";
            default -> "vnd";
        };
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String buildTransactionNote(String orderCode, String reason) {
        if (reason == null || reason.isBlank()) {
            return "Refund credit for orderCode=" + orderCode;
        }

        return "Refund credit for orderCode=" + orderCode + ", reason=" + reason;
    }
}