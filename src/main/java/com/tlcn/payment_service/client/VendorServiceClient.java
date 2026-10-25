package com.tlcn.payment_service.client;

import com.tlcn.payment_service.dto.ResponseDTO;
import com.tlcn.payment_service.dto.VendorBankAccountResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
@Slf4j
public class VendorServiceClient {

    private final RestTemplate restTemplate;

    @Value("${vendor-service.url:http://localhost:8081}")
    private String vendorServiceUrl;

    public ResponseDTO<VendorBankAccountResponse> getMyVerifiedBankAccount() {
        return exchangeForBankAccount(vendorServiceUrl + "/vendors/bank-account/my-verified");
    }

    public ResponseDTO<VendorBankAccountResponse> getVerifiedBankAccount(String vendorKeycloakId) {
        return exchangeForBankAccount(vendorServiceUrl + "/vendors/" + vendorKeycloakId + "/bank-account/verified");
    }

    @SuppressWarnings("null")
    private ResponseDTO<VendorBankAccountResponse> exchangeForBankAccount(String url) {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());

        try {
            ResponseEntity<ResponseDTO<VendorBankAccountResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<ResponseDTO<VendorBankAccountResponse>>() {}
            );

            return response.getBody();
        } catch (RestClientException ex) {
            log.error("Vendor service call failed: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null && attributes.getRequest() != null) {
            String authorizationHeader = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
            }
        }

        return headers;
    }
}
