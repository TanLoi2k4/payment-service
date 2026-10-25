package com.tlcn.payment_service.exception;

import lombok.Getter;

@Getter
public class PayoutTransferException extends RuntimeException {

    private final boolean terminal;

    private PayoutTransferException(String message, boolean terminal, Throwable cause) {
        super(message, cause);
        this.terminal = terminal;
    }

    public static PayoutTransferException terminal(String message) {
        return new PayoutTransferException(message, true, null);
    }

    public static PayoutTransferException terminal(String message, Throwable cause) {
        return new PayoutTransferException(message, true, cause);
    }

    public static PayoutTransferException retryable(String message) {
        return new PayoutTransferException(message, false, null);
    }

    public static PayoutTransferException retryable(String message, Throwable cause) {
        return new PayoutTransferException(message, false, cause);
    }
}
