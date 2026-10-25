package com.tlcn.payment_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "vnpay.payout")
@Data
@Validated
public class VnpayPayoutProperties {
    private String apiUrl;
    private String merchantId;
    private String apiKey;
    private String hashSecret;
    private String queryUrl;
}