package com.tlcn.payment_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Component
@Data
@Validated
@ConfigurationProperties(prefix = "vnpay")
public class VnpayProperties {
    
    @NotBlank(message = "VNPAY Merchant ID must not be blank")
    private String merchantId;
    
    @NotBlank(message = "VNPAY Hash Secret must not be blank")
    private String hashSecret;
    
    @NotBlank(message = "VNPAY API URL must not be blank")
    private String apiUrl;
    
    @NotBlank(message = "VNPAY Return URL must not be blank")
    private String returnUrl;

    /** VNPay redirect after vendor fee invoice checkout (ecommerce-vendor). */
    private String vendorReturnUrl;
    
    @NotBlank(message = "VNPAY IPN URL must not be blank")
    private String ipnUrl;
    
    private String locale;
    
    public VnpayProperties() {
        this.locale = "vn";
    }
}