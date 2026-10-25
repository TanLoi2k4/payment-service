package com.tlcn.payment_service.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tlcn.payment_service.dto.CreatePaymentResponse;
import com.tlcn.payment_service.dto.ResponseDTO;
import com.tlcn.payment_service.dto.WalletBalanceResponse;
import com.tlcn.payment_service.dto.WalletTopupRequest;
import com.tlcn.payment_service.dto.WalletTransactionResponse;
import com.tlcn.payment_service.service.PaymentService;
import com.tlcn.payment_service.service.WalletService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payments/customers/wallet")
@RequiredArgsConstructor
public class CustomerWalletController {

    private final WalletService walletService;
    private final PaymentService paymentService;

    @GetMapping(value = "/balance", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ResponseDTO<WalletBalanceResponse>> getMyWalletBalance(Authentication authentication) {
        WalletBalanceResponse response = walletService.getMyWalletBalance(authentication.getName());
        return ResponseEntity.ok(new ResponseDTO<>(true, "Wallet balance retrieved successfully", response));
    }

    @GetMapping(value = "/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ResponseDTO<List<WalletTransactionResponse>>> getMyWalletTransactions(Authentication authentication) {
        List<WalletTransactionResponse> response = walletService.getMyWalletTransactions(authentication.getName());
        return ResponseEntity.ok(new ResponseDTO<>(true, "Wallet transactions retrieved successfully", response));
    }

    @PostMapping(value = "/topup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ResponseDTO<CreatePaymentResponse>> initiateWalletTopup(
        @Valid @RequestBody WalletTopupRequest request,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        Authentication authentication
    ) throws Exception {
        CreatePaymentResponse response = paymentService.initiateWalletTopup(
            authentication.getName(),
            request.getAmount(),
            idempotencyKey
        );
        return ResponseEntity.ok(new ResponseDTO<>(true, "Wallet top-up initiated successfully", response));
    }
}