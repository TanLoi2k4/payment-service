package com.tlcn.payment_service.controller;

import com.tlcn.payment_service.dto.ResponseDTO;
import com.tlcn.payment_service.enums.PayoutStatus;
import com.tlcn.payment_service.model.VendorPayout;
import com.tlcn.payment_service.repository.VendorPayoutRepository;
import com.tlcn.payment_service.service.VendorPayoutService;
import com.tlcn.payment_service.dto.RejectPayoutRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments/admin/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminPayoutController {

    private final VendorPayoutRepository vendorPayoutRepository;
    private final VendorPayoutService payoutService;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<VendorPayout>>> getPayoutsByStatus(@RequestParam(defaultValue = "PENDING_APPROVAL") PayoutStatus status) {
        List<VendorPayout> payouts = vendorPayoutRepository.findByStatus(status);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payouts retrieved successfully", payouts));
    }

    @PostMapping("/{payoutId}/approve")
    public ResponseEntity<ResponseDTO<VendorPayout>> approvePayout(@PathVariable Long payoutId) {
        VendorPayout payout = payoutService.approvePayout(payoutId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payout approved successfully", payout));
    }

    @PostMapping("/{payoutId}/reject")
    public ResponseEntity<ResponseDTO<VendorPayout>> rejectPayout(@PathVariable Long payoutId, @Valid @RequestBody RejectPayoutRequest request) {
        VendorPayout payout = payoutService.rejectPayout(payoutId, request.getReason());
        return ResponseEntity.ok(new ResponseDTO<>(true, "Payout rejected successfully", payout));
    }

    @PostMapping("/{payoutId}/release-items")
    public ResponseEntity<ResponseDTO<VendorPayout>> releaseFailedPayoutItems(@PathVariable Long payoutId) {
        VendorPayout payout = payoutService.releaseFailedPayoutItems(payoutId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Items from failed payout have been released for the next cycle.", payout));
    }
}