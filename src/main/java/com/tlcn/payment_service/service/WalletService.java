package com.tlcn.payment_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tlcn.payment_service.exception.BusinessException;
import com.tlcn.payment_service.dto.WalletBalanceResponse;
import com.tlcn.payment_service.dto.WalletTransactionResponse;
import com.tlcn.payment_service.enums.WalletTransactionType;
import com.tlcn.payment_service.model.Payment;
import com.tlcn.payment_service.model.Wallet;
import com.tlcn.payment_service.model.WalletTransaction;
import com.tlcn.payment_service.repository.WalletRepository;
import com.tlcn.payment_service.repository.WalletTransactionRepository;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletService {

    private static final String INITIATOR_SYSTEM = "SYSTEM";
    private static final String PAYMENT_DEBIT_CORRELATION_PREFIX = "PAYMENT_DEBIT:";
    private static final String TOPUP_CREDIT_CORRELATION_PREFIX = "TOPUP_CREDIT:";

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional(readOnly = true)
    public WalletBalanceResponse getMyWalletBalance(String customerKeycloakId) {
        return walletRepository.findByCustomerKeycloakId(customerKeycloakId)
            .map(this::toBalanceResponse)
            .orElseGet(() -> WalletBalanceResponse.builder()
                .walletId(null)
                .customerKeycloakId(customerKeycloakId)
                .balance(BigDecimal.ZERO)
                .points(0L)
                .walletExists(false)
                .createdAt(null)
                .lastUpdatedAt(null)
                .build());
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> getMyWalletTransactions(String customerKeycloakId) {
        return walletRepository.findByCustomerKeycloakId(customerKeycloakId)
            .map(wallet -> walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId())
                .stream()
                .map(WalletTransactionResponse::from)
                .collect(Collectors.toList()))
            .orElseGet(Collections::emptyList);
    }

    @Transactional
    public WalletDebitResult debitForPayment(Payment payment) {
        if (payment == null) {
            return WalletDebitResult.failure("WALLET_PAYMENT_INVALID", "Payment is required");
        }

        if (payment.getCustomerKeycloakId() == null || payment.getCustomerKeycloakId().isBlank()) {
            return WalletDebitResult.failure("WALLET_CUSTOMER_REQUIRED", "Customer wallet cannot be resolved");
        }

        BigDecimal amount = normalizeMoney(payment.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return WalletDebitResult.failure("WALLET_AMOUNT_INVALID", "Payment amount must be greater than zero");
        }

        // Sử dụng khóa bi quan để ngăn chặn race condition
        Wallet wallet = walletRepository.findByCustomerKeycloakIdWithLock(payment.getCustomerKeycloakId())
            .orElseThrow(() -> new BusinessException("Wallet not found for customer", HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND"));
        if (wallet == null) { // This check is now redundant due to orElseThrow but kept for clarity
             return WalletDebitResult.failure("WALLET_NOT_FOUND", "Wallet not found for customer");
        }

        BigDecimal previousBalance = normalizeMoney(wallet.getBalance());
        if (previousBalance.compareTo(amount) < 0) {
            return WalletDebitResult.failure("WALLET_INSUFFICIENT_BALANCE", "Insufficient wallet balance");
        }

        BigDecimal afterBalance = previousBalance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        wallet.setBalance(afterBalance);
        wallet = walletRepository.save(wallet);

        WalletTransaction transaction = WalletTransaction.builder()
            .wallet(wallet)
            .payment(payment)
            .transactionType(WalletTransactionType.PAYMENT_DEBIT)
            .amount(amount)
            .previousBalance(previousBalance)
            .afterBalance(afterBalance)
            .initiator(INITIATOR_SYSTEM)
            .correlationId(buildPaymentDebitCorrelationId(payment.getOrderCode()))
            .note(buildPaymentDebitNote(payment.getOrderCode()))
            .build();
        walletTransactionRepository.save(transaction);

        return WalletDebitResult.success(previousBalance, afterBalance, wallet.getId(), transaction.getId());
    }

    /**
     * Credit wallet after a successful VNPay top-up payment.
     * Idempotent via unique correlationId per orderCode.
     */
    @Transactional
    public void creditForTopup(Payment payment) {
        if (payment == null) {
            throw new BusinessException("Payment is required", HttpStatus.BAD_REQUEST, "WALLET_TOPUP_PAYMENT_REQUIRED");
        }
        if (payment.getCustomerKeycloakId() == null || payment.getCustomerKeycloakId().isBlank()) {
            throw new BusinessException("Customer wallet cannot be resolved", HttpStatus.BAD_REQUEST, "WALLET_CUSTOMER_REQUIRED");
        }

        BigDecimal amount = normalizeMoney(payment.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Top-up amount must be greater than zero", HttpStatus.BAD_REQUEST, "WALLET_TOPUP_AMOUNT_INVALID");
        }

        String correlationId = buildTopupCreditCorrelationId(payment.getOrderCode());
        if (walletTransactionRepository.findByCorrelationId(correlationId).isPresent()) {
            return;
        }

        Wallet wallet = getOrCreateWalletWithLock(payment.getCustomerKeycloakId());
        BigDecimal previousBalance = normalizeMoney(wallet.getBalance());
        BigDecimal afterBalance = previousBalance.add(amount).setScale(2, RoundingMode.HALF_UP);

        wallet.setBalance(afterBalance);
        wallet = walletRepository.save(wallet);

        WalletTransaction transaction = WalletTransaction.builder()
            .wallet(wallet)
            .payment(payment)
            .transactionType(WalletTransactionType.TOPUP_CREDIT)
            .amount(amount)
            .previousBalance(previousBalance)
            .afterBalance(afterBalance)
            .initiator(INITIATOR_SYSTEM)
            .correlationId(correlationId)
            .note(buildTopupCreditNote(payment.getOrderCode()))
            .build();
        walletTransactionRepository.save(transaction);
    }

    private Wallet getOrCreateWalletWithLock(String customerKeycloakId) {
        Optional<Wallet> existing = walletRepository.findByCustomerKeycloakIdWithLock(customerKeycloakId);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            walletRepository.saveAndFlush(Wallet.builder()
                .customerKeycloakId(customerKeycloakId)
                .balance(BigDecimal.ZERO)
                .points(0L)
                .build());
        } catch (DataIntegrityViolationException ex) {
            // Concurrent create — fall through to locked read
        }

        return walletRepository.findByCustomerKeycloakIdWithLock(customerKeycloakId)
            .orElseThrow(() -> new BusinessException(
                "Wallet not found after create for customer",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "WALLET_CREATE_RACE"
            ));
    }

    private String buildTopupCreditCorrelationId(String orderCode) {
        return TOPUP_CREDIT_CORRELATION_PREFIX + (orderCode == null ? "UNKNOWN" : orderCode.trim());
    }

    private String buildTopupCreditNote(String orderCode) {
        if (orderCode == null || orderCode.isBlank()) {
            return "Wallet top-up via VNPay";
        }
        return "Wallet top-up via VNPay orderCode=" + orderCode.trim();
    }

    private WalletBalanceResponse toBalanceResponse(Wallet wallet) {
        return WalletBalanceResponse.builder()
            .walletId(wallet.getId())
            .customerKeycloakId(wallet.getCustomerKeycloakId())
            .balance(wallet.getBalance())
            .points(wallet.getPoints())
            .walletExists(true)
            .createdAt(wallet.getCreatedAt())
            .lastUpdatedAt(wallet.getLastUpdatedAt())
            .build();
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String buildPaymentDebitCorrelationId(String orderCode) {
        return PAYMENT_DEBIT_CORRELATION_PREFIX + (orderCode == null ? "UNKNOWN" : orderCode.trim());
    }

    private String buildPaymentDebitNote(String orderCode) {
        if (orderCode == null || orderCode.isBlank()) {
            return "Wallet debit for payment";
        }

        return "Wallet debit for orderCode=" + orderCode.trim();
    }

    @Data
    @Builder
    public static class WalletDebitResult {
        private boolean success;
        private String failureCode;
        private String failureMessage;
        private BigDecimal previousBalance;
        private BigDecimal afterBalance;
        private Long walletId;
        private Long walletTransactionId;

        public static WalletDebitResult success(BigDecimal previousBalance, BigDecimal afterBalance, Long walletId, Long walletTransactionId) {
            return WalletDebitResult.builder()
                .success(true)
                .previousBalance(previousBalance)
                .afterBalance(afterBalance)
                .walletId(walletId)
                .walletTransactionId(walletTransactionId)
                .build();
        }

        public static WalletDebitResult failure(String failureCode, String failureMessage) {
            return WalletDebitResult.builder()
                .success(false)
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .build();
        }
    }
}