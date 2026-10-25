package com.tlcn.payment_service.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@RequestMapping("/payments/vendor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
public class VendorPaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    @PutMapping(value = "/{orderCode}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<PaymentDetailResponse>> updatePayment(
        @PathVariable String orderCode,
        @Valid @RequestBody UpdatePaymentRequest request,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        PaymentDetailResponse response = paymentService.updatePendingPaymentByVendor(orderCode, request, idempotencyKey);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payment updated successfully", response));
    }

    @PostMapping(value = "/{orderCode}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<PaymentDetailResponse>> cancelPayment(
        @PathVariable String orderCode,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        PaymentDetailResponse response = paymentService.cancelPendingPaymentByVendor(orderCode, idempotencyKey);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payment cancelled successfully", response));
    }

    @PostMapping(value = "/{orderCode}/refund", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<RefundResponse>> refundPayment(
        @PathVariable String orderCode,
        @Valid @RequestBody RefundRequest request,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        request.setOrderCode(orderCode);
        // Customer is resolved from payment record; do not bind vendor auth as customer.
        request.setCustomerKeycloakId(null);
        RefundResponse response = RefundResponse.from(refundService.requestRefund(request, idempotencyKey));
        return ResponseEntity.ok(new ResponseDTO<>(true, "Refund created successfully", response));
    }
}
