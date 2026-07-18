package io.coreplatform.billing.application.exception;

public class InsufficientPermissionException extends RuntimeException {

    public InsufficientPermissionException(String message) {
        super(message);
    }
}