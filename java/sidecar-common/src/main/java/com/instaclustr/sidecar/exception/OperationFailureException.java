package com.instaclustr.sidecar.exception;

public class OperationFailureException extends RuntimeException {

    public OperationFailureException(final String message) {
        super(message);
    }

    public OperationFailureException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
