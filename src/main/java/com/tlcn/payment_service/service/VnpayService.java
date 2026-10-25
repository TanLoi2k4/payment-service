package com.tlcn.payment_service.service;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import com.tlcn.payment_service.config.VnpayProperties;
import com.tlcn.payment_service.enums.PaymentType;
import com.tlcn.payment_service.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VnpayService {

    private final VnpayProperties vnpayProperties;
    private static final String HMAC_SHA512 = "HmacSHA512";

    /**
     * Build VNPAY checkout URL with signature
     */
    public String buildCheckoutUrl(Payment payment) throws Exception {
        return buildCheckoutUrl(payment, generateTxnRef(payment));
    }

    /**
     * Build VNPAY checkout URL with a provided transaction reference.
     */
    public String buildCheckoutUrl(Payment payment, String txnRef) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();

        // Required VNPAY parameters
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnpayProperties.getMerchantId());
        params.put("vnp_Locale", vnpayProperties.getLocale());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Order: " + payment.getOrderCode());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Amount", toVnpayAmount(payment));
        params.put("vnp_ReturnUrl", resolveReturnUrl(payment));
        params.put("vnp_IpnUrl", vnpayProperties.getIpnUrl());
        
        // Optional but recommended
        params.put("vnp_CreateDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        params.put("vnp_ExpireDate", LocalDateTime.now().plusMinutes(15).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

        // Calculate secure hash
        String secureHash = calculateSecureHash(params, vnpayProperties.getHashSecret());
        params.put("vnp_SecureHash", secureHash);

        // Build query string
        String queryString = buildQueryString(params);
        String checkoutUrl = vnpayProperties.getApiUrl() + "?" + queryString;

        log.info("Built VNPAY checkout URL for orderCode={}, txnRef={}", payment.getOrderCode(), txnRef);
        return checkoutUrl;
    }

    private String resolveReturnUrl(Payment payment) {
        if (payment.getPaymentType() == PaymentType.VENDOR_FEE) {
            String vendorReturnUrl = vnpayProperties.getVendorReturnUrl();
            if (vendorReturnUrl != null && !vendorReturnUrl.isBlank()) {
                return vendorReturnUrl;
            }
        }
        return vnpayProperties.getReturnUrl();
    }

    /**
     * Verify IPN signature from VNPAY
     */
    public boolean verifyIpnSignature(Map<String, String> params, String providedHash) {
        try {
            // Remove secure hash from params for recalculation
            Map<String, String> sortedParams = new LinkedHashMap<>(params);
            sortedParams.remove("vnp_SecureHash");
            sortedParams.remove("vnp_SecureHashType");

            String calculatedHash = calculateSecureHash(sortedParams, vnpayProperties.getHashSecret());
            boolean isValid = calculatedHash.equalsIgnoreCase(providedHash);

            if (!isValid) {
                log.warn("Invalid VNPAY IPN signature. Expected: {}, Got: {}", calculatedHash, providedHash);
            }

            return isValid;
        } catch (Exception e) {
            log.error("Error verifying VNPAY IPN signature", e);
            return false;
        }
    }

    /**
     * Calculate HmacSHA512 hash from sorted parameters
     */
    private String calculateSecureHash(Map<String, String> params, String secret) throws Exception {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder hashData = new StringBuilder();
        for (String key : keys) {
            if (hashData.length() > 0) {
                hashData.append("&");
            }
            hashData.append(URLEncoder.encode(key, StandardCharsets.UTF_8.toString()));
            hashData.append("=");
            hashData.append(URLEncoder.encode(params.get(key), StandardCharsets.UTF_8.toString()));
        }

        Mac hmac = Mac.getInstance(HMAC_SHA512);
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA512);
        hmac.init(keySpec);

        byte[] hash = hmac.doFinal(hashData.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }

        return hexString.toString();
    }

    /**
     * Build query string from sorted parameters
     */
    private String buildQueryString(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder queryString = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first) {
                first = false;
            } else {
                queryString.append("&");
            }
            queryString.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.toString()));
            queryString.append("=");
            queryString.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString()));
        }

        return queryString.toString();
    }

    /**
     * VNPAY expects amount in the smallest currency unit (VND x 100).
     */
    public String toVnpayAmount(Payment payment) {
        BigDecimal amount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
        long amountInMinorUnits = amount.setScale(2, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .longValueExact();
        return Long.toString(amountInMinorUnits);
    }

    public boolean matchesVnpayAmount(Payment payment, String vnpAmount) {
        if (vnpAmount == null || vnpAmount.isBlank()) {
            return false;
        }
        return toVnpayAmount(payment).equals(vnpAmount.trim());
    }

    /**
     * Generate transaction reference from payment info
     */
    private String generateTxnRef(Payment payment) {
        return payment.getOrderCode() + "_" + System.currentTimeMillis();
    }
}
