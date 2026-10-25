package com.tlcn.payment_service.controller;

import com.tlcn.payment_service.service.SettlementService;
import com.tlcn.payment_service.service.VnpaySettlementService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhooks/settlement")
@RequiredArgsConstructor
@Slf4j
public class SettlementWebhookController {
    
    private final SettlementService settlementService;
    private final VnpaySettlementService vnpaySettlementService;

    /**
     * Endpoint để VNPAY gọi lại sau khi xử lý xong lệnh chuyển tiền (chi hộ).
     */
    @PostMapping("/vnpay")
    public ResponseEntity<Map<String, String>> handleVnpaySettlementWebhook(
            @RequestBody Map<String, String> payload,
            @RequestHeader("x-vnp-signature") String signature) {
        
        String payoutCode = payload.get("vnp_TxnRef");
        String responseCode = payload.get("vnp_ResponseCode");
        log.info("Received VNPAY settlement webhook for payoutCode: {}, responseCode: {}", payoutCode, responseCode);
        
        // 1. Verify signature
        if (!vnpaySettlementService.verifyWebhookSignature(payload, signature)) {
            log.warn("Invalid signature for VNPAY settlement webhook. PayoutCode: {}", payoutCode);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("RspCode", "97", "Message", "Invalid Signature"));
        }
        
        // 2. Process the payout finalization
        boolean isSuccess = "00".equals(responseCode);
        String failureReason = isSuccess ? null : "VNPAY Payout failed with code: " + responseCode;
        
        settlementService.finalizePayout(payoutCode, isSuccess, failureReason);
        
        // 3. Respond to VNPAY
        return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Confirm Success"));
    }
}