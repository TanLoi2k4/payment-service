package com.tlcn.payment_service.controller;

import com.tlcn.payment_service.dto.PlatformPayableToVendorResponse;
import com.tlcn.payment_service.dto.ResponseDTO;
import com.tlcn.payment_service.dto.VendorFinanceSummaryResponse;
import com.tlcn.payment_service.service.VendorFinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payments/vendor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
public class VendorFinanceController {

    private final VendorFinanceService vendorFinanceService;

    @GetMapping(value = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<VendorFinanceSummaryResponse>> getFinanceSummary(Authentication authentication) {
        VendorFinanceSummaryResponse summary = vendorFinanceService.getSummaryForVendor(authentication.getName());
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor finance summary retrieved successfully", summary));
    }

    @GetMapping(value = "/payables", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<List<PlatformPayableToVendorResponse>>> getPendingPayables(Authentication authentication) {
        List<PlatformPayableToVendorResponse> payables =
            vendorFinanceService.getPendingPayablesForVendor(authentication.getName());
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor payables retrieved successfully", payables));
    }
}
