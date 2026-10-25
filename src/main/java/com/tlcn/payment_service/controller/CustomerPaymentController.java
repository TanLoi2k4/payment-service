package com.tlcn.payment_service.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tlcn.payment_service.dto.CreatePaymentRequest;
import com.tlcn.payment_service.dto.CreatePaymentResponse;
import com.tlcn.payment_service.dto.PaymentDetailResponse;
import com.tlcn.payment_service.dto.RefundRequest;
import com.tlcn.payment_service.dto.RefundResponse;
import com.tlcn.payment_service.dto.ResponseDTO;
import com.tlcn.payment_service.dto.UpdatePaymentRequest;
import com.tlcn.payment_service.service.PaymentService;
import com.tlcn.payment_service.service.RefundService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class CustomerPaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    @PostMapping(value = "/customers/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ResponseDTO<CreatePaymentResponse>> createPayment(
        @Valid @RequestBody CreatePaymentRequest request,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        Authentication authentication
    ) throws Exception {
        request.setCustomerKeycloakId(authentication.getName());
        CreatePaymentResponse response = paymentService.createPayment(request, idempotencyKey);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payment created successfully", response));
    }

    @PutMapping(value = "/customers/{orderCode}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ResponseDTO<PaymentDetailResponse>> updatePayment(
        @PathVariable String orderCode,
        @Valid @RequestBody UpdatePaymentRequest request,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        Authentication authentication
    ) {
        PaymentDetailResponse response = paymentService.updatePendingPayment(orderCode, authentication.getName(), request, idempotencyKey);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payment updated successfully", response));
    }

    @PostMapping(value = "/customers/{orderCode}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ResponseDTO<PaymentDetailResponse>> cancelPayment(
        @PathVariable String orderCode,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        Authentication authentication
    ) {
        PaymentDetailResponse response = paymentService.cancelPendingPayment(orderCode, authentication.getName(), idempotencyKey);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payment cancelled successfully", response));
    }

    @PostMapping(value = "/customers/{orderCode}/refund", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ResponseDTO<RefundResponse>> refundPayment(
        @PathVariable String orderCode,
        @Valid @RequestBody RefundRequest request,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        Authentication authentication
    ) {
        request.setOrderCode(orderCode);
        request.setCustomerKeycloakId(authentication.getName());
        RefundResponse response = RefundResponse.from(refundService.requestRefund(request, idempotencyKey));
        return ResponseEntity.ok(new ResponseDTO<>(true, "Refund created successfully", response));
    }

    @GetMapping(value = "/customers/{orderCode}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ResponseDTO<PaymentDetailResponse>> getPaymentByOrderCode(
        @PathVariable String orderCode,
        Authentication authentication
    ) {
        PaymentDetailResponse response = paymentService.getPaymentByOrderCode(orderCode, authentication.getName());
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payment retrieved successfully", response));
    }

    @GetMapping(value = "/vnpay-return", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<Map<String, Object>>> vnpayReturn(
        @RequestParam Map<String, String> params
    ) {
        Map<String, Object> response = paymentService.handleVnpayReturn(params);
        return ResponseEntity.ok(new ResponseDTO<>(true, "VNPAY return processed successfully", response));
    }

    @PostMapping(value = "/vnpay-ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> vnpayIpn(
        @RequestParam Map<String, String> params
    ) {
        return ResponseEntity.ok(paymentService.handleVnpayIpn(params));
    }
}
