package com.tlcn.payment_service.service;

import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tlcn.payment_service.dto.CreatePaymentRequest;
import com.tlcn.payment_service.dto.CreatePaymentResponse;
import com.tlcn.payment_service.dto.PaymentMethodBreakdownResponse;
import com.tlcn.payment_service.dto.PaymentDetailResponse;
import com.tlcn.payment_service.dto.UpdatePaymentRequest;
import com.tlcn.payment_service.dto.PlatformPayableToVendorResponse;
import com.tlcn.payment_service.dto.VendorPayableToPlatformResponse;
import com.tlcn.payment_service.dto.kafka.PaymentFailed;
import com.tlcn.payment_service.dto.kafka.PaymentSuccess;
import com.tlcn.payment_service.enums.PaymentMethod;
import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.enums.PaymentType;
import com.tlcn.payment_service.enums.InvoiceStatus;
import com.tlcn.payment_service.exception.BusinessException;
import com.tlcn.payment_service.exception.ForbiddenException;
import com.tlcn.payment_service.exception.ResourceNotFoundException;
import com.tlcn.payment_service.kafka.PaymentEventPublisher;
import com.tlcn.payment_service.model.Payment;
import com.tlcn.payment_service.model.PlatformPayableToVendor;
import com.tlcn.payment_service.model.VendorPayableToPlatform;
import com.tlcn.payment_service.model.VendorInvoice;
import com.tlcn.payment_service.model.VnpayCheckoutRef;
import com.tlcn.payment_service.model.VnpayPaymentEvent;
import com.tlcn.payment_service.repository.PaymentRepository;
import com.tlcn.payment_service.repository.PlatformPayableToVendorRepository;
import com.tlcn.payment_service.repository.VendorInvoiceRepository;
import com.tlcn.payment_service.repository.VendorPayableToPlatformRepository;
import com.tlcn.payment_service.repository.VnpayCheckoutRefRepository;
import com.tlcn.payment_service.repository.VnpayPaymentEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String PROVIDER_COD = "COD";
    private static final String PROVIDER_VNPAY = "VNPAY";
    private static final String PROVIDER_WALLET = "WALLET";

    private final PaymentRepository paymentRepository;
    private final PlatformPayableToVendorRepository platformPayableToVendorRepository;
    private final VendorPayableToPlatformRepository vendorPayableToPlatformRepository;
    private final VendorInvoiceRepository vendorInvoiceRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final VnpayPaymentEventRepository vnpayPaymentEventRepository;
    private final VnpayCheckoutRefRepository vnpayCheckoutRefRepository;
    private final VnpayService vnpayService;
    private final WalletService walletService;

    @Transactional
    @SuppressWarnings("null")
    public CreatePaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) throws Exception {
        if (request == null) {
            throw new BusinessException("CreatePaymentRequest is required", HttpStatus.BAD_REQUEST, "PAYMENT_REQUEST_REQUIRED");
        }
        if (request.getOrderCode() == null || request.getOrderCode().isBlank()) {
            throw new BusinessException("orderCode is required", HttpStatus.BAD_REQUEST, "PAYMENT_ORDER_CODE_REQUIRED");
        }
        if (request.getPaymentMethod() == null) {
            throw new BusinessException("paymentMethod is required", HttpStatus.BAD_REQUEST, "PAYMENT_METHOD_REQUIRED");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("amount must be greater than zero", HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_INVALID");
        }

        String effectiveIdempotencyKey = (idempotencyKey != null && !idempotencyKey.isBlank())
            ? idempotencyKey
            : request.getOrderCode();

        Optional<Payment> existingByIdempotency = paymentRepository.findByIdempotencyKey(effectiveIdempotencyKey);
        if (existingByIdempotency.isPresent()) {
            Payment payment = existingByIdempotency.get();
            if (!matchesCreateRequest(payment, request)) {
                throw new BusinessException(
                    "Idempotency key already used with a different request",
                    HttpStatus.CONFLICT,
                    "PAYMENT_IDEMPOTENCY_CONFLICT"
                );
            }
            return toCreateResponse(payment);
        }

        Payment payment = Payment.builder()
            .orderCode(request.getOrderCode())
            .orderId(request.getOrderId())
            .customerKeycloakId(request.getCustomerKeycloakId())
            .amount(request.getAmount())
            .currency(normalizeCurrency(request.getCurrency()))
            .paymentMethod(request.getPaymentMethod())
            .status(PaymentStatus.PENDING)
            .provider(resolveProvider(request.getPaymentMethod()))
            .requestedAt(Instant.now())
            .paymentType(request.getPaymentType() == null ? PaymentType.CUSTOMER_ORDER : request.getPaymentType())
            .idempotencyKey(effectiveIdempotencyKey)
            .build();

        Payment savedPayment = paymentRepository.save(payment);
        payment = Objects.requireNonNull(savedPayment, "savedPayment");

        if (request.getPaymentMethod() == PaymentMethod.VNPAY) {
            payment = createVnpayCheckout(payment);
        } else if (request.getPaymentMethod() == PaymentMethod.WALLET) {
            payment = settleWalletPayment(payment);
        }

        payment = paymentRepository.save(payment);

        return toCreateResponse(payment);
    }

    /**
     * Initiate wallet top-up via VNPay checkout.
     * orderCode format: TOPUP-{16-char hex}
     */
    @Transactional
    public CreatePaymentResponse initiateWalletTopup(
        String customerKeycloakId,
        BigDecimal amount,
        String idempotencyKey
    ) throws Exception {
        if (customerKeycloakId == null || customerKeycloakId.isBlank()) {
            throw new BusinessException("customerKeycloakId is required", HttpStatus.BAD_REQUEST, "WALLET_TOPUP_CUSTOMER_REQUIRED");
        }
        if (amount == null || amount.compareTo(new BigDecimal("10000")) < 0) {
            throw new BusinessException("amount must be at least 10000 VND", HttpStatus.BAD_REQUEST, "WALLET_TOPUP_AMOUNT_INVALID");
        }

        String orderCode = "TOPUP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        CreatePaymentRequest request = CreatePaymentRequest.builder()
            .orderCode(orderCode)
            .customerKeycloakId(customerKeycloakId)
            .amount(amount)
            .currency("VND")
            .paymentMethod(PaymentMethod.VNPAY)
            .paymentType(PaymentType.WALLET_TOPUP)
            .build();

        return createPayment(request, idempotencyKey);
    }

    private boolean matchesCreateRequest(Payment payment, CreatePaymentRequest request) {
        PaymentType requestedType = request.getPaymentType() == null ? PaymentType.CUSTOMER_ORDER : request.getPaymentType();

        return Objects.equals(payment.getOrderCode(), request.getOrderCode())
            && Objects.equals(payment.getOrderId(), request.getOrderId())
            && Objects.equals(payment.getCustomerKeycloakId(), request.getCustomerKeycloakId())
            && normalizeMoney(payment.getAmount()).equals(normalizeMoney(request.getAmount()))
            && payment.getPaymentMethod() == request.getPaymentMethod()
            && Objects.equals(payment.getPaymentType(), requestedType);
    }

    private Payment settleWalletPayment(Payment payment) {
        WalletService.WalletDebitResult result = walletService.debitForPayment(payment);

        payment.setProvider(PROVIDER_WALLET);
        payment.setProviderPaymentIntentId(null);
        payment.setProviderCheckoutSessionId(null);
        payment.setCheckoutUrl(null);

        if (result.isSuccess()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(Instant.now());
            payment.setFailureCode(null);
            payment.setFailureMessage(null);
            publishSuccess(payment);
            return payment;
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailedAt(Instant.now());
        payment.setFailureCode(result.getFailureCode());
        payment.setFailureMessage(result.getFailureMessage());
        publishFailed(payment);
        return payment;
    }

    private Payment createVnpayCheckout(Payment payment) {
        try {
            if (payment.getId() == null) {
                payment = paymentRepository.saveAndFlush(payment);
            }

            String previousTxnRef = payment.getProviderPaymentIntentId();
            String txnRef = generateVnpayTxnRef(payment);
            String checkoutUrl = vnpayService.buildCheckoutUrl(payment, txnRef);

            vnpayCheckoutRefRepository.supersedeActiveRefs(
                payment.getId(),
                VnpayCheckoutRef.Status.ACTIVE,
                VnpayCheckoutRef.Status.SUPERSEDED
            );
            vnpayCheckoutRefRepository.saveAndFlush(
                VnpayCheckoutRef.builder()
                    .txnRef(txnRef)
                    .paymentId(payment.getId())
                    .amount(normalizeMoney(payment.getAmount()))
                    .status(VnpayCheckoutRef.Status.ACTIVE)
                    .build()
            );

            payment.setProviderPaymentIntentId(txnRef);
            payment.setCheckoutUrl(checkoutUrl);
            if (previousTxnRef != null && !previousTxnRef.isBlank() && !previousTxnRef.equals(txnRef)) {
                log.info(
                    "Superseded VNPAY txnRef={} for orderCode={}, newTxnRef={}",
                    previousTxnRef,
                    payment.getOrderCode(),
                    txnRef
                );
            }
            return paymentRepository.save(payment);
        } catch (Exception e) {
            log.error("Failed to build VNPAY checkout URL for orderCode={}", payment.getOrderCode(), e);
            throw new RuntimeException("Failed to build VNPAY checkout URL", e);
        }
    }

    @Transactional
    public PaymentDetailResponse updatePendingPayment(String orderCode, String requesterKeycloakId, UpdatePaymentRequest request, String idempotencyKey) {
        Payment payment = paymentRepository.findByOrderCode(orderCode)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for orderCode=" + orderCode));

        if (!Objects.equals(payment.getCustomerKeycloakId(), requesterKeycloakId)) {
            throw new ForbiddenException("You do not have permission to update this payment");
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank() && payment.getIdempotencyKey() != null
            && !Objects.equals(payment.getIdempotencyKey(), idempotencyKey)) {
            throw new BusinessException(
                "Idempotency key does not match payment request",
                HttpStatus.CONFLICT,
                "PAYMENT_IDEMPOTENCY_MISMATCH"
            );
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(
                "Only pending payments can be updated",
                HttpStatus.CONFLICT,
                "PAYMENT_NOT_PENDING"
            );
        }

        BigDecimal updatedAmount = normalizeMoney(request.getAmount());
        payment.setAmount(updatedAmount);
        payment.setCurrency(normalizeCurrency(request.getCurrency()));
        payment.setRequestedAt(Instant.now());

        if (payment.getPaymentMethod() == PaymentMethod.VNPAY) {
            payment = createVnpayCheckout(payment);
        } else {
            payment.setProvider(resolveProvider(payment.getPaymentMethod()));
            payment.setProviderPaymentIntentId(null);
            payment.setProviderCheckoutSessionId(null);
            payment.setCheckoutUrl(null);
        }

        payment = paymentRepository.save(payment);
        return toDetailResponse(payment);
    }

    @Transactional
    public PaymentDetailResponse updatePendingPaymentByVendor(String orderCode, UpdatePaymentRequest request, String idempotencyKey) {
        Payment payment = paymentRepository.findByOrderCode(orderCode)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for orderCode=" + orderCode));

        if (idempotencyKey != null && !idempotencyKey.isBlank() && payment.getIdempotencyKey() != null
            && !Objects.equals(payment.getIdempotencyKey(), idempotencyKey)) {
            throw new BusinessException(
                "Idempotency key does not match payment request",
                HttpStatus.CONFLICT,
                "PAYMENT_IDEMPOTENCY_MISMATCH"
            );
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(
                "Only pending payments can be updated",
                HttpStatus.CONFLICT,
                "PAYMENT_NOT_PENDING"
            );
        }

        BigDecimal updatedAmount = normalizeMoney(request.getAmount());
        payment.setAmount(updatedAmount);
        payment.setCurrency(normalizeCurrency(request.getCurrency()));
        payment.setRequestedAt(Instant.now());

        if (payment.getPaymentMethod() == PaymentMethod.VNPAY) {
            payment = createVnpayCheckout(payment);
        } else {
            payment.setProvider(resolveProvider(payment.getPaymentMethod()));
            payment.setProviderPaymentIntentId(null);
            payment.setProviderCheckoutSessionId(null);
            payment.setCheckoutUrl(null);
        }

        payment = paymentRepository.save(payment);
        return toDetailResponse(payment);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> handleVnpayReturn(Map<String, String> params) {
        Map<String, Object> response = new LinkedHashMap<>();

        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String secureHash = params.get("vnp_SecureHash");

        if (!vnpayService.verifyIpnSignature(params, secureHash)) {
            response.put("success", false);
            response.put("message", "Invalid VNPAY signature");
            return response;
        }

        Payment payment = resolvePaymentFromVnpayParams(params);
        if (payment == null) {
            response.put("success", false);
            response.put("message", "Payment not found");
            response.put("txnRef", txnRef);
            return response;
        }

        response.put("success", "00".equals(responseCode));
        response.put("orderCode", payment.getOrderCode());
        response.put("paymentId", payment.getId());
        response.put("paymentStatus", payment.getStatus());
        response.put("paymentType", payment.getPaymentType());
        response.put("provider", payment.getProvider());
        response.put("txnRef", payment.getProviderPaymentIntentId());
        response.put("responseCode", responseCode);
        response.put("transactionNo", params.get("vnp_TransactionNo"));
        response.put("message", "00".equals(responseCode)
            ? (payment.getStatus() == PaymentStatus.COMPLETED ? "Payment completed" : "Payment received, waiting for confirmation")
            : "Payment not completed");

        return response;
    }

    @Transactional
    public Map<String, Object> handleVnpayIpn(Map<String, String> params) {
        Map<String, Object> response = new LinkedHashMap<>();
        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String secureHash = params.get("vnp_SecureHash");
        String vnpAmount = params.get("vnp_Amount");

        if (!vnpayService.verifyIpnSignature(params, secureHash)) {
            response.put("RspCode", "97");
            response.put("Message", "Invalid signature");
            return response;
        }

        if (txnRef == null || txnRef.isBlank()) {
            response.put("RspCode", "01");
            response.put("Message", "Missing transaction reference");
            return response;
        }

        // Only the currently ACTIVE checkout txnRef is accepted.
        // Updating amount supersedes older refs so stale checkout links are rejected.
        Payment payment = resolveActiveVnpayPayment(txnRef);
        if (payment == null) {
            response.put("RspCode", "01");
            response.put("Message", "Payment not found or txnRef is no longer active");
            return response;
        }

        payment = paymentRepository.findByOrderCodeWithLock(payment.getOrderCode()).orElse(payment);
        if (!txnRef.equals(payment.getProviderPaymentIntentId())) {
            response.put("RspCode", "01");
            response.put("Message", "Payment not found or txnRef is no longer active");
            return response;
        }

        if (!vnpayService.matchesVnpayAmount(payment, vnpAmount)) {
            log.warn(
                "VNPAY IPN amount mismatch for orderCode={}, txnRef={}, expected={}, actual={}",
                payment.getOrderCode(),
                txnRef,
                vnpayService.toVnpayAmount(payment),
                vnpAmount
            );
            response.put("RspCode", "04");
            response.put("Message", "Invalid amount");
            return response;
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            response.put("RspCode", "00");
            response.put("Message", "Payment already finalized");
            return response;
        }

        // Reserve only after payment is resolved and validated so failed lookups never lock txnRef.
        if (!reserveVnpayEvent(txnRef)) {
            response.put("RspCode", "02");
            response.put("Message", "Duplicate transaction");
            return response;
        }

        if ("00".equals(responseCode)) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(Instant.now());
            payment.setProvider(PROVIDER_VNPAY);
            payment.setProviderPaymentIntentId(txnRef);
            paymentRepository.save(payment);

            if (payment.getPaymentType() == PaymentType.WALLET_TOPUP) {
                walletService.creditForTopup(payment);
            } else if (payment.getPaymentType() == PaymentType.VENDOR_FEE) {
                // Fail loudly inside the same TX so payment/invoice stay consistent; VNPAY will retry IPN.
                markVendorFeeInvoicePaid(payment);
                publishSuccess(payment);
            } else {
                publishSuccess(payment);
            }

            confirmActiveCheckoutRef(txnRef);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailedAt(Instant.now());
            payment.setProvider(PROVIDER_VNPAY);
            payment.setFailureCode(responseCode);
            payment.setFailureMessage(buildVnpayFailureMessage(responseCode, params.get("vnp_Message")));
            paymentRepository.save(payment);
            if (payment.getPaymentType() != PaymentType.WALLET_TOPUP) {
                publishFailed(payment);
            }
        }

        markVnpayEventProcessed(txnRef);
        response.put("RspCode", "00");
        response.put("Message", "Confirm Success");
        return response;
    }

    private boolean reserveVnpayEvent(String txnRef) {
        try {
            vnpayPaymentEventRepository.saveAndFlush(
                VnpayPaymentEvent.builder()
                    .txnRef(txnRef)
                    .build()
            );
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private void markVnpayEventProcessed(String txnRef) {
        VnpayPaymentEvent event = vnpayPaymentEventRepository.findByTxnRef(txnRef).orElse(null);
        if (event == null) {
            return;
        }
        event.setStatus(VnpayPaymentEvent.VnpayEventStatus.PROCESSED);
        event.setProcessedAt(Instant.now());
        vnpayPaymentEventRepository.save(event);
    }

    /**
     * Resolves payment only via an ACTIVE VnpayCheckoutRef (or current providerPaymentIntentId fallback).
     * Superseded checkout links are rejected.
     */
    private Payment resolveActiveVnpayPayment(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) {
            return null;
        }

        Optional<VnpayCheckoutRef> activeRef = vnpayCheckoutRefRepository.findByTxnRefAndStatus(
            txnRef,
            VnpayCheckoutRef.Status.ACTIVE
        );
        if (activeRef.isPresent()) {
            return paymentRepository.findById(activeRef.get().getPaymentId()).orElse(null);
        }

        // Backward-compatible fallback for payments created before checkout-ref tracking.
        return paymentRepository.findByProviderPaymentIntentId(txnRef)
            .filter(payment -> txnRef.equals(payment.getProviderPaymentIntentId()))
            .orElse(null);
    }

    private void confirmActiveCheckoutRef(String txnRef) {
        vnpayCheckoutRefRepository.findByTxnRefAndStatus(txnRef, VnpayCheckoutRef.Status.ACTIVE)
            .ifPresent(ref -> {
                ref.setStatus(VnpayCheckoutRef.Status.CONFIRMED);
                vnpayCheckoutRefRepository.save(ref);
            });
    }

    private Payment resolvePaymentFromVnpayParams(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        Payment byActiveTxnRef = resolveActiveVnpayPayment(txnRef);
        if (byActiveTxnRef != null) {
            return byActiveTxnRef;
        }

        // Return URL may still arrive with a superseded txnRef; surface payment by orderCode for UX only.
        // IPN must never use this fallback (see handleVnpayIpn → resolveActiveVnpayPayment).
        if (txnRef != null && !txnRef.isBlank()) {
            String orderCode = extractOrderCodeFromTxnRef(txnRef);
            if (orderCode != null) {
                Payment byOrderCode = paymentRepository.findByOrderCode(orderCode).orElse(null);
                if (byOrderCode != null) {
                    return byOrderCode;
                }
            }
        }

        String orderInfo = params.get("vnp_OrderInfo");
        if (orderInfo != null && !orderInfo.isBlank()) {
            return paymentRepository.findByOrderCode(extractOrderCodeFromOrderInfo(orderInfo)).orElse(null);
        }

        return null;
    }

    private String extractOrderCodeFromTxnRef(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) {
            return null;
        }

        // Expect txnRef format: <orderCode>_<timestamp>
        // Use a regex to ensure the suffix is a numeric timestamp so we don't split on
        // underscores that belong to the orderCode itself.
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("_(\\d{10,})$");
        java.util.regex.Matcher m = p.matcher(txnRef);
        if (m.find()) {
            return txnRef.substring(0, m.start());
        }

        // If no trailing numeric timestamp found, consider this unparsable.
        return null;
    }

    private String extractOrderCodeFromOrderInfo(String orderInfo) {
        String prefix = "Order: ";
        if (orderInfo.startsWith(prefix)) {
            return orderInfo.substring(prefix.length()).trim();
        }
        return orderInfo.trim();
    }

    private String buildVnpayFailureMessage(String responseCode, String vnpMessage) {
        if (vnpMessage != null && !vnpMessage.isBlank()) {
            return vnpMessage;
        }
        return "VNPAY payment failed with code=" + responseCode;
    }

    private String generateVnpayTxnRef(Payment payment) {
        return payment.getOrderCode() + "_" + System.currentTimeMillis();
    }

    private String resolveProvider(PaymentMethod paymentMethod) {
        return switch (paymentMethod) {
            case COD -> PROVIDER_COD;
            case VNPAY -> PROVIDER_VNPAY;
            case WALLET -> PROVIDER_WALLET;
        };
    }

    @Transactional
    public CreatePaymentResponse completeCodPayment(String orderCode) {
        Payment payment = paymentRepository.findByOrderCodeWithLock(orderCode)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for orderCode=" + orderCode));

        if (payment.getPaymentMethod() != PaymentMethod.COD) {
            throw new IllegalArgumentException("Payment is not COD for orderCode=" + orderCode);
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return toCreateResponse(payment);
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(
                "Payment cannot be completed from status=" + payment.getStatus(),
                HttpStatus.CONFLICT,
                "PAYMENT_NOT_PENDING"
            );
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(Instant.now());
        payment.setProvider(PROVIDER_COD);
        payment = paymentRepository.save(payment);

        publishSuccess(payment);
        return toCreateResponse(payment);
    }

    @Transactional
    public PaymentDetailResponse cancelPendingPayment(String orderCode, String requesterKeycloakId, String idempotencyKey) {
        Payment payment = paymentRepository.findByOrderCode(orderCode)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for orderCode=" + orderCode));

        if (!Objects.equals(payment.getCustomerKeycloakId(), requesterKeycloakId)) {
            throw new ForbiddenException("You do not have permission to cancel this payment");
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank() && payment.getIdempotencyKey() != null
            && !Objects.equals(payment.getIdempotencyKey(), idempotencyKey)) {
            throw new BusinessException(
                "Idempotency key does not match payment request",
                HttpStatus.CONFLICT,
                "PAYMENT_IDEMPOTENCY_MISMATCH"
            );
        }

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            return toDetailResponse(payment);
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(
                "Only pending payments can be cancelled",
                HttpStatus.CONFLICT,
                "PAYMENT_NOT_PENDING"
            );
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCancelledAt(Instant.now());
        payment.setFailureCode("CANCELLED_BY_CUSTOMER");
        payment.setFailureMessage("Payment cancelled by customer");
        payment = paymentRepository.save(payment);

        return toDetailResponse(payment);
    }

    @Transactional
    public PaymentDetailResponse cancelPendingPaymentByVendor(String orderCode, String idempotencyKey) {
        Payment payment = paymentRepository.findByOrderCode(orderCode)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for orderCode=" + orderCode));

        if (idempotencyKey != null && !idempotencyKey.isBlank() && payment.getIdempotencyKey() != null
            && !Objects.equals(payment.getIdempotencyKey(), idempotencyKey)) {
            throw new BusinessException(
                "Idempotency key does not match payment request",
                HttpStatus.CONFLICT,
                "PAYMENT_IDEMPOTENCY_MISMATCH"
            );
        }

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            return toDetailResponse(payment);
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(
                "Only pending payments can be cancelled",
                HttpStatus.CONFLICT,
                "PAYMENT_NOT_PENDING"
            );
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCancelledAt(Instant.now());
        payment.setFailureCode("CANCELLED_BY_VENDOR");
        payment.setFailureMessage("Payment cancelled by vendor flow");
        payment = paymentRepository.save(payment);

        return toDetailResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentDetailResponse getPaymentByOrderCode(String orderCode, String requesterKeycloakId) {
        Payment payment = paymentRepository.findByOrderCode(orderCode)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for orderCode=" + orderCode));

        if (!Objects.equals(payment.getCustomerKeycloakId(), requesterKeycloakId)) {
            throw new ForbiddenException("You do not have permission to view this payment");
        }

        return toDetailResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PlatformPayableToVendorResponse> getPlatformPayablesForVendor(String vendorKeycloakId, String orderCode, Long subOrderId) {
        if (vendorKeycloakId == null || vendorKeycloakId.isBlank()) {
            throw new ForbiddenException("Vendor keycloak id is required");
        }

        return platformPayableToVendorRepository.findByVendorKeycloakId(vendorKeycloakId).stream()
            .filter(payable -> orderCode == null || orderCode.isBlank() || orderCode.equals(payable.getOrderCode()))
            .filter(payable -> subOrderId == null || subOrderId.equals(payable.getSubOrderId()))
            .map(this::toPlatformPayableToVendorResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<VendorPayableToPlatformResponse> getVendorPayablesForVendor(String vendorKeycloakId, String orderCode, Long subOrderId) {
        if (vendorKeycloakId == null || vendorKeycloakId.isBlank()) {
            throw new ForbiddenException("Vendor keycloak id is required");
        }

        return vendorPayableToPlatformRepository.findByVendorKeycloakId(vendorKeycloakId).stream()
            .filter(payable -> orderCode == null || orderCode.isBlank() || orderCode.equals(payable.getOrderCode()))
            .filter(payable -> subOrderId == null || subOrderId.equals(payable.getSubOrderId()))
            .map(this::toVendorPayableToPlatformResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformPayableToVendorResponse> getPlatformPayablesForAdmin(String vendorKeycloakId, String orderCode, Long subOrderId) {
        var platformEntities = (vendorKeycloakId == null || vendorKeycloakId.isBlank())
            ? platformPayableToVendorRepository.findAll()
            : platformPayableToVendorRepository.findByVendorKeycloakId(vendorKeycloakId);

        return platformEntities.stream()
            .filter(payable -> orderCode == null || orderCode.isBlank() || orderCode.equals(payable.getOrderCode()))
            .filter(payable -> subOrderId == null || subOrderId.equals(payable.getSubOrderId()))
            .map(this::toPlatformPayableToVendorResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<VendorPayableToPlatformResponse> getVendorPayablesForAdmin(String vendorKeycloakId, String orderCode, Long subOrderId) {
        var vendorEntities = (vendorKeycloakId == null || vendorKeycloakId.isBlank())
            ? vendorPayableToPlatformRepository.findAll()
            : vendorPayableToPlatformRepository.findByVendorKeycloakId(vendorKeycloakId);

        return vendorEntities.stream()
            .filter(payable -> orderCode == null || orderCode.isBlank() || orderCode.equals(payable.getOrderCode()))
            .filter(payable -> subOrderId == null || subOrderId.equals(payable.getSubOrderId()))
            .map(this::toVendorPayableToPlatformResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PaymentDetailResponse getPaymentByOrderCodeAsAdmin(String orderCode) {
        Payment payment = paymentRepository.findByOrderCode(orderCode)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found for orderCode=" + orderCode));
        return toDetailResponse(payment);
    }

    @Transactional(readOnly = true)
    public Page<PaymentDetailResponse> listTransactionsForAdmin(
        PaymentStatus status,
        PaymentMethod paymentMethod,
        String search,
        int page,
        int size,
        String sortBy
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        String sortField = sortBy != null && !sortBy.isBlank() ? sortBy : "createdAt";

        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("paymentType"), PaymentType.CUSTOMER_ORDER));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (paymentMethod != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("orderCode")), pattern),
                    cb.like(cb.lower(root.get("customerKeycloakId")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Payment> payments = paymentRepository.findAll(
            spec,
            PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, sortField))
        );

        return payments.map(PaymentService::toDetailResponse);
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodBreakdownResponse> getPaymentMethodBreakdownForAdmin() {
        List<Object[]> rows = paymentRepository.sumAmountByPaymentMethod(
            PaymentType.CUSTOMER_ORDER,
            PaymentStatus.COMPLETED
        );

        BigDecimal total = rows.stream()
            .map(row -> (BigDecimal) row[1])
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return rows.stream()
            .map(row -> {
                PaymentMethod method = (PaymentMethod) row[0];
                BigDecimal amount = (BigDecimal) row[1];
                long count = row[2] instanceof Number number ? number.longValue() : 0L;
                double percentage = total.compareTo(BigDecimal.ZERO) > 0
                    ? amount.multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, RoundingMode.HALF_UP)
                        .doubleValue()
                    : 0.0;

                return PaymentMethodBreakdownResponse.builder()
                    .paymentMethod(method)
                    .amount(amount)
                    .transactionCount(count)
                    .percentage(percentage)
                    .build();
            })
            .toList();
    }

    public PlatformPayableToVendorResponse toPlatformPayableToVendorResponse(PlatformPayableToVendor payable) {
        return PlatformPayableToVendorResponse.builder()
            .id(payable.getId())
            .orderCode(payable.getOrderCode())
            .orderId(payable.getOrderId())
            .subOrderId(payable.getSubOrderId())
            .vendorKeycloakId(payable.getVendorKeycloakId())
            .amount(payable.getAmount())
            .status(payable.getStatus())
            .paidAt(payable.getPaidAt())
            .createdAt(payable.getCreatedAt())
            .updatedAt(payable.getUpdatedAt())
            .build();
    }

    private VendorPayableToPlatformResponse toVendorPayableToPlatformResponse(VendorPayableToPlatform payable) {
        return VendorPayableToPlatformResponse.builder()
            .id(payable.getId())
            .orderCode(payable.getOrderCode())
            .orderId(payable.getOrderId())
            .subOrderId(payable.getSubOrderId())
            .vendorKeycloakId(payable.getVendorKeycloakId())
            .amount(payable.getAmount())
            .commissionRate(payable.getCommissionRate())
            .status(payable.getStatus())
            .paidAt(payable.getPaidAt())
            .createdAt(payable.getCreatedAt())
            .updatedAt(payable.getUpdatedAt())
            .build();
    }

    private void publishSuccess(Payment payment) {
        PaymentSuccess event = PaymentSuccess.builder()
            .orderCode(payment.getOrderCode())
            .orderId(payment.getOrderId())
            .customerKeycloakId(payment.getCustomerKeycloakId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .provider(payment.getProvider())
            .providerPaymentIntentId(payment.getProviderPaymentIntentId())
            .paidAt(payment.getPaidAt())
            .build();

        paymentEventPublisher.publishPaymentSuccess(payment.getOrderCode(), event);
    }

    /**
     * Marks the linked vendor invoice (and fee lines) as paid for a completed VENDOR_FEE payment.
     * Throws on failure so the surrounding transaction can roll back and keep payment/invoice consistent.
     */
    @Transactional
    public void markVendorFeeInvoicePaid(Payment payment) {
        VendorInvoice invoice = vendorInvoiceRepository.findByPaymentId(payment.getId())
            .or(() -> vendorInvoiceRepository.findByInvoiceCode(payment.getOrderCode()))
            .or(() -> vendorInvoiceRepository.findByPaymentProviderPaymentIntentId(payment.getProviderPaymentIntentId()))
            .orElseThrow(() -> new BusinessException(
                "Vendor invoice not found for completed VENDOR_FEE payment orderCode=" + payment.getOrderCode(),
                HttpStatus.CONFLICT,
                "VENDOR_INVOICE_NOT_FOUND"
            ));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return;
        }

        invoice.setStatus(InvoiceStatus.PAID);
        if (invoice.getPayment() == null) {
            invoice.setPayment(payment);
        }
        vendorInvoiceRepository.save(invoice);

        if (invoice.getPayableItems() != null) {
            for (VendorPayableToPlatform fee : invoice.getPayableItems()) {
                fee.setStatus(PaymentStatus.COMPLETED);
                fee.setPaidAt(payment.getPaidAt());
            }
            vendorPayableToPlatformRepository.saveAll(invoice.getPayableItems());
        }

        log.info(
            "Marked invoice {} and {} fees as PAID for paymentId={}",
            invoice.getInvoiceCode(),
            invoice.getPayableItems() == null ? 0 : invoice.getPayableItems().size(),
            payment.getId()
        );
    }

    private void publishFailed(Payment payment) {
        PaymentFailed event = PaymentFailed.builder()
            .orderCode(payment.getOrderCode())
            .orderId(payment.getOrderId())
            .customerKeycloakId(payment.getCustomerKeycloakId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .provider(payment.getProvider())
            .providerPaymentIntentId(payment.getProviderPaymentIntentId())
            .failureCode(payment.getFailureCode())
            .failureMessage(payment.getFailureMessage())
            .failedAt(payment.getFailedAt())
            .build();

        paymentEventPublisher.publishPaymentFailed(payment.getOrderCode(), event);
    }

    private static String normalizeCurrency(String currency) {
        // Force VND as the single supported currency for the system.
        if (currency == null || currency.isBlank()) {
            return "vnd";
        }
        String c = currency.trim().toLowerCase();
        return switch (c) {
            case "vnd", "vnđ" -> "vnd";
            default -> "vnd";
        };
    }

    private static BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static CreatePaymentResponse toCreateResponse(Payment payment) {
        return CreatePaymentResponse.builder()
            .paymentId(payment.getId())
            .orderCode(payment.getOrderCode())
            .status(payment.getStatus())
            .provider(payment.getProvider())
            .providerCheckoutSessionId(payment.getProviderCheckoutSessionId())
            .providerPaymentIntentId(payment.getProviderPaymentIntentId())
            .checkoutUrl(payment.getCheckoutUrl())
            .build();
    }

    private static PaymentDetailResponse toDetailResponse(Payment payment) {
        return PaymentDetailResponse.builder()
            .paymentId(payment.getId())
            .orderCode(payment.getOrderCode())
            .orderId(payment.getOrderId())
            .customerKeycloakId(payment.getCustomerKeycloakId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentMethod(payment.getPaymentMethod())
            .status(payment.getStatus())
            .provider(payment.getProvider())
            .providerPaymentIntentId(payment.getProviderPaymentIntentId())
            .providerCheckoutSessionId(payment.getProviderCheckoutSessionId())
            .checkoutUrl(payment.getCheckoutUrl())
            .failureCode(payment.getFailureCode())
            .failureMessage(payment.getFailureMessage())
            .requestedAt(payment.getRequestedAt())
            .paidAt(payment.getPaidAt())
            .failedAt(payment.getFailedAt())
                .cancelledAt(payment.getCancelledAt())
            .createdAt(payment.getCreatedAt())
            .updatedAt(payment.getUpdatedAt())
            .build();
    }

    @Transactional(readOnly = true)
    public Payment getPaymentOrThrow(Long id) {
        Objects.requireNonNull(id, "id");
        return paymentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id=" + id));
    }
}
