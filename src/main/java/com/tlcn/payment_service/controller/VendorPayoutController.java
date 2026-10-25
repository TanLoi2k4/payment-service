package com.tlcn.payment_service.controller;

import com.tlcn.payment_service.dto.ResponseDTO;
import com.tlcn.payment_service.model.VendorPayout;
import com.tlcn.payment_service.repository.VendorPayoutRepository;
import com.tlcn.payment_service.service.VendorPayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payments/vendor/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
public class VendorPayoutController {

    private final VendorPayoutRepository vendorPayoutRepository;
    private final VendorPayoutService payoutService;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<VendorPayout>>> getMyPayoutHistory(Authentication authentication) {
        List<VendorPayout> payouts = vendorPayoutRepository.findByVendorKeycloakIdOrderByCreatedAtDesc(authentication.getName());
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payout history retrieved successfully", payouts));
    }

    @PostMapping("/request-payout")
    public ResponseEntity<ResponseDTO<VendorPayout>> requestPayout(Authentication authentication) {
        VendorPayout payout = payoutService.requestPayout(authentication.getName());
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payout request created successfully", payout));
    }
}