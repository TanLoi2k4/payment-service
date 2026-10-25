package com.tlcn.payment_service.service;

import com.tlcn.payment_service.config.VnpayPayoutProperties;
import com.tlcn.payment_service.exception.PayoutTransferException;
import com.tlcn.payment_service.model.VendorPayout;
import com.tlcn.payment_service.repository.VendorPayoutRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class VnpaySettlementService {

    private final RestTemplate restTemplate;
    private final VendorPayoutRepository vendorPayoutRepository;
    private final VnpayPayoutProperties vnpayPayoutProperties;
    private static final String HMAC_SHA512 = "HmacSHA512";

    public VnpaySettlementService(@Qualifier("externalRestTemplate") RestTemplate restTemplate,
                                  VendorPayoutRepository vendorPayoutRepository,
                                  VnpayPayoutProperties vnpayPayoutProperties) {
        this.restTemplate = restTemplate;
        this.vendorPayoutRepository = vendorPayoutRepository;
        this.vnpayPayoutProperties = vnpayPayoutProperties;
    }

    /**
     * Thực hiện việc gọi API chuyển tiền (Chi hộ) của đối tác thanh toán.
     * Phương thức này sẽ gửi yêu cầu chuyển tiền và cập nhật mã giao dịch của đối tác.
     *
     * @param payout Lệnh chi trả cần thực hiện.
     * @throws Exception Nếu có lỗi trong quá trình gọi API.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void transferToVendor(VendorPayout payout) {
        log.info("Initiating transfer for Payout Code: {}, Amount: {}, Bank: {}, Account: {}",
                payout.getPayoutCode(),
                payout.getNetAmount(),
                payout.getBankCode(),
                maskAccountNumber(payout.getBankAccountNumber()));

        String apiUrl = vnpayPayoutProperties.getApiUrl();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", vnpayPayoutProperties.getApiKey());

        Map<String, String> requestBody = new LinkedHashMap<>();
        requestBody.put("vnp_Version", "2.1.0");
        requestBody.put("vnp_TmnCode", vnpayPayoutProperties.getMerchantId());
        requestBody.put("vnp_CreateDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        requestBody.put("vnp_TxnRef", payout.getPayoutCode());
        requestBody.put("vnp_Amount", String.valueOf(payout.getNetAmount().longValue()));
        requestBody.put("vnp_BankCode", payout.getBankCode());
        requestBody.put("vnp_AccountNo", payout.getBankAccountNumber());
        requestBody.put("vnp_AccountName", payout.getBankAccountName());
        requestBody.put("vnp_OrderInfo", "Payout for vendor " + payout.getVendorKeycloakId());

        try {
            String secureHash = calculateSecureHash(requestBody, vnpayPayoutProperties.getHashSecret());
            requestBody.put("vnp_SecureHash", secureHash);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()
                && response.getBody() != null
                && "00".equals(String.valueOf(response.getBody().get("vnp_ResponseCode")))) {
                String providerTxnId = (String) response.getBody().get("vnp_TransactionNo");
                payout.setProviderTransactionId(providerTxnId);
                vendorPayoutRepository.save(payout);
                log.info("Transfer request sent successfully for Payout {}. Provider TXN ID: {}", payout.getPayoutCode(), providerTxnId);
                return;
            }

            // Explicit VNPAY/business rejection — terminal, do not retry as APPROVED.
            log.error("Terminal transfer rejection for payout {}. Response: {}", payout.getPayoutCode(), response.getBody());
            throw PayoutTransferException.terminal("Failed to initiate transfer: " + response.getBody());
        } catch (PayoutTransferException ex) {
            throw ex;
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw PayoutTransferException.terminal(
                    "VNPAY client error for payout " + payout.getPayoutCode() + ": " + ex.getStatusCode(),
                    ex
                );
            }
            throw PayoutTransferException.retryable(
                "VNPAY server/transient error for payout " + payout.getPayoutCode() + ": " + ex.getStatusCode(),
                ex
            );
        } catch (ResourceAccessException ex) {
            throw PayoutTransferException.retryable(
                "Network error calling VNPAY for payout " + payout.getPayoutCode(),
                ex
            );
        } catch (RestClientException ex) {
            throw PayoutTransferException.retryable(
                "Rest client error calling VNPAY for payout " + payout.getPayoutCode(),
                ex
            );
        } catch (Exception ex) {
            throw PayoutTransferException.retryable(
                "Unexpected error calling VNPAY for payout " + payout.getPayoutCode(),
                ex
            );
        }
    }

    /**
     * Truy vấn trạng thái giao dịch chi hộ từ VNPAY.
     *
     * @param payout Lệnh chi trả cần truy vấn.
     * @return Map chứa thông tin phản hồi từ VNPAY.
     * @throws Exception Nếu có lỗi xảy ra.
     */
    @SuppressWarnings("rawtypes")
    public Map<String, String> queryTransactionStatus(VendorPayout payout) throws Exception {
        log.info("Querying VNPAY transaction status for Payout Code: {}", payout.getPayoutCode());

        String apiUrl = vnpayPayoutProperties.getQueryUrl();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", vnpayPayoutProperties.getApiKey());

        Map<String, String> requestBody = new LinkedHashMap<>();
        requestBody.put("vnp_Version", "2.1.0");
        requestBody.put("vnp_Command", "querydr"); // Lệnh truy vấn
        requestBody.put("vnp_TmnCode", vnpayPayoutProperties.getMerchantId());
        requestBody.put("vnp_TxnRef", payout.getPayoutCode()); // Mã giao dịch của merchant
        requestBody.put("vnp_TransactionDate", DateTimeFormatter.ofPattern("yyyyMMdd").format(payout.getCreatedAt().atZone(java.time.ZoneId.systemDefault())));
        requestBody.put("vnp_CreateDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        requestBody.put("vnp_IpAddr", "127.0.0.1"); // IP của server
        requestBody.put("vnp_OrderInfo", "Query payout status for " + payout.getPayoutCode());

        String secureHash = calculateSecureHash(requestBody, vnpayPayoutProperties.getHashSecret());
        requestBody.put("vnp_SecureHash", secureHash);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            log.info("VNPAY query response for {}: {}", payout.getPayoutCode(), response.getBody());
            @SuppressWarnings("unchecked")
            Map<String, String> responseBody = response.getBody();
            return responseBody;
        } else {
            log.error("Failed to query VNPAY transaction status for payout {}. Response: {}", payout.getPayoutCode(), response.getBody());
            throw new RuntimeException("Failed to query VNPAY transaction status: " + response.getBody());
        }
    }

    public boolean verifyWebhookSignature(Map<String, String> params, String providedHash) {
        try {
            Map<String, String> sortedParams = new LinkedHashMap<>(params);
            sortedParams.remove("vnp_SecureHash");

            String calculatedHash = calculateSecureHash(sortedParams, vnpayPayoutProperties.getHashSecret());
            boolean isValid = calculatedHash.equalsIgnoreCase(providedHash);

            if (!isValid) {
                log.warn("Invalid VNPAY Payout webhook signature. PayoutCode: {}. Expected: {}, Got: {}", params.get("vnp_TxnRef"), calculatedHash, providedHash);
            }

            return isValid;
        } catch (Exception e) {
            log.error("Error verifying VNPAY Payout webhook signature for PayoutCode: {}", params.get("vnp_TxnRef"), e);
            return false;
        }
    }

    private String calculateSecureHash(Map<String, String> params, String secret) throws Exception {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder hashData = new StringBuilder();
        for (String key : keys) {
            if (params.get(key) == null || params.get(key).isEmpty()) continue;
            if (hashData.length() > 0) {
                hashData.append("|");
            }
            hashData.append(key).append("=").append(params.get(key));
        }

        Mac hmac = Mac.getInstance(HMAC_SHA512);
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA512);
        hmac.init(keySpec);

        byte[] hash = hmac.doFinal(hashData.toString().getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}