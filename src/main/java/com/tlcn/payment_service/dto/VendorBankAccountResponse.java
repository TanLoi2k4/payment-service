package com.tlcn.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VendorBankAccountResponse {
    private String accountNumber;
    private String accountName;
    private String bankCode;
    private String bankName;
    private Boolean isVerified;
}