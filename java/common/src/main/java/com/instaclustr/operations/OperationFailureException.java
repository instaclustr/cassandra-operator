package com.instaclustr.operations;

public class OperationFailureException extends RuntimeException {

    public OperationFailureException(final String message) {
        super(message);
    }

    public OperationFailureException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
