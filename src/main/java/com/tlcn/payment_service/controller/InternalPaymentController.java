package com.tlcn.payment_service.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tlcn.payment_service.dto.CreatePaymentResponse;
import com.tlcn.payment_service.dto.ResponseDTO;
import com.tlcn.payment_service.service.PaymentService;

import lombok.RequiredArgsConstructor;

/**
 * Internal service-to-service endpoints (called via Eureka, not user JWT).
 */
@RestController
@RequestMapping("/payments/internal")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentService paymentService;

    @PostMapping(value = "/{orderCode}/complete-cod", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<CreatePaymentResponse>> completeCodPayment(
        @PathVariable String orderCode
    ) {
        CreatePaymentResponse response = paymentService.completeCodPayment(orderCode);
        return ResponseEntity.ok(new ResponseDTO<>(true, "COD payment completed successfully", response));
    }
}
