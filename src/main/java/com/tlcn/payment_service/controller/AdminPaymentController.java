package com.tlcn.payment_service.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.tlcn.payment_service.dto.PaymentDetailResponse;
import com.tlcn.payment_service.dto.PaymentMethodBreakdownResponse;
import com.tlcn.payment_service.dto.PlatformPayableToVendorResponse;
import com.tlcn.payment_service.dto.ResponseDTO;
import com.tlcn.payment_service.dto.VendorPayableToPlatformResponse;
import com.tlcn.payment_service.enums.PaymentMethod;
import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.service.PaymentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payments/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping(value = "/platform-payables", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<List<PlatformPayableToVendorResponse>>> getPlatformPayables(
        @RequestParam(name = "orderCode", required = false) String orderCode,
        @RequestParam(name = "subOrderId", required = false) Long subOrderId,
        @RequestParam(name = "vendorKeycloakId", required = false) String vendorKeycloakId
    ) {
        List<PlatformPayableToVendorResponse> response = paymentService.getPlatformPayablesForAdmin(vendorKeycloakId, orderCode, subOrderId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Platform payables retrieved successfully", response));
    }

    @GetMapping(value = "/vendor-payables", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<List<VendorPayableToPlatformResponse>>> getVendorPayables(
        @RequestParam(name = "orderCode", required = false) String orderCode,
        @RequestParam(name = "subOrderId", required = false) Long subOrderId,
        @RequestParam(name = "vendorKeycloakId", required = false) String vendorKeycloakId
    ) {
        List<VendorPayableToPlatformResponse> response = paymentService.getVendorPayablesForAdmin(vendorKeycloakId, orderCode, subOrderId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor payables retrieved successfully", response));
    }

    @GetMapping(value = "/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<Page<PaymentDetailResponse>>> listTransactions(
        @RequestParam(required = false) PaymentStatus status,
        @RequestParam(required = false) PaymentMethod paymentMethod,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy
    ) {
        Page<PaymentDetailResponse> response = paymentService.listTransactionsForAdmin(
            status,
            paymentMethod,
            search,
            page,
            size,
            sortBy
        );
        return ResponseEntity.ok(new ResponseDTO<>(true, "Transactions retrieved successfully", response));
    }

    @GetMapping(value = "/payment-methods-breakdown", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<List<PaymentMethodBreakdownResponse>>> getPaymentMethodBreakdown() {
        List<PaymentMethodBreakdownResponse> response = paymentService.getPaymentMethodBreakdownForAdmin();
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payment method breakdown retrieved successfully", response));
    }

    @GetMapping(value = "/{orderCode}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<PaymentDetailResponse>> getPaymentByOrderCode(
        @PathVariable String orderCode
    ) {
        PaymentDetailResponse response = paymentService.getPaymentByOrderCodeAsAdmin(orderCode);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payment retrieved successfully", response));
    }
}
