package com.tlcn.payment_service.exception;

import org.springframework.http.HttpStatus;

public class KeycloakException extends BusinessException {
    public KeycloakException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    public KeycloakException(String message, HttpStatus status) {
        super(message, status);
    }
}