package com.tlcn.payment_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectPayoutRequest {
    @NotBlank(message = "Reason for rejection cannot be blank")
    private String reason;
}