package com.tlcn.payment_service.controller;

import com.tlcn.payment_service.dto.CreatePaymentResponse;
import com.tlcn.payment_service.dto.ResponseDTO;
import com.tlcn.payment_service.dto.VendorInvoiceResponse;
import com.tlcn.payment_service.enums.InvoiceStatus;
import com.tlcn.payment_service.service.VendorInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payments/vendor/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
public class VendorInvoiceController {

    private final VendorInvoiceService vendorInvoiceService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<List<VendorInvoiceResponse>>> getMyInvoices(
            Authentication authentication,
            @RequestParam(required = false) InvoiceStatus status) {
        List<VendorInvoiceResponse> invoices = vendorInvoiceService.getInvoicesForVendor(authentication.getName(), status);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor invoices retrieved successfully", invoices));
    }

    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<List<VendorInvoiceResponse>>> getMyPaymentHistory(Authentication authentication) {
        List<VendorInvoiceResponse> invoices = vendorInvoiceService.getInvoicesForVendor(authentication.getName(), InvoiceStatus.PAID);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor payment history retrieved successfully", invoices));
    }

    @GetMapping(value = "/{invoiceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<VendorInvoiceResponse>> getMyInvoiceDetail(
            Authentication authentication,
            @PathVariable Long invoiceId) {
        VendorInvoiceResponse invoice = vendorInvoiceService.getInvoiceForVendor(authentication.getName(), invoiceId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor invoice retrieved successfully", invoice));
    }

    @PostMapping(value = "/{invoiceId}/pay", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseDTO<CreatePaymentResponse>> payInvoice(
            Authentication authentication,
            @PathVariable Long invoiceId) throws Exception {
        CreatePaymentResponse response = vendorInvoiceService.createPaymentForInvoice(authentication.getName(), invoiceId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Invoice payment created successfully", response));
    }
}