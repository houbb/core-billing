package io.coreplatform.billing.application.exception;

public class BillingBusinessException extends RuntimeException {

    public enum Kind {
        NOT_FOUND,
        CONFLICT,
        UNPROCESSABLE,
        FORBIDDEN
    }

    private final String errorCode;
    private final Kind kind;

    public BillingBusinessException(String errorCode, String message, Kind kind) {
        super(message);
        this.errorCode = errorCode;
        this.kind = kind;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Kind getKind() {
        return kind;
    }

    public static BillingBusinessException notFound(String code, String message) {
        return new BillingBusinessException(code, message, Kind.NOT_FOUND);
    }

    public static BillingBusinessException conflict(String code, String message) {
        return new BillingBusinessException(code, message, Kind.CONFLICT);
    }

    public static BillingBusinessException unprocessable(String code, String message) {
        return new BillingBusinessException(code, message, Kind.UNPROCESSABLE);
    }

    public static BillingBusinessException forbidden(String code, String message) {
        return new BillingBusinessException(code, message, Kind.FORBIDDEN);
    }
}

