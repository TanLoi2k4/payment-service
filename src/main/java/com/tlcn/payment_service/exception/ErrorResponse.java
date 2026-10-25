package com.tlcn.payment_service.exception;

import java.time.Instant;

public class ErrorResponse {
    private boolean success = false;
    private String message;
    private String errorCode;
    private long timestamp = Instant.now().toEpochMilli();

    public ErrorResponse() {}

    public ErrorResponse(String message) {
        this.message = message;
    }

    public ErrorResponse(String message, String errorCode) {
        this.message = message;
        this.errorCode = errorCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}