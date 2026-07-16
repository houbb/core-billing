package io.coreplatform.billing.api.exception;

public enum ErrorCode {

    ACCOUNT_NOT_FOUND("BILLING_ACCOUNT_NOT_FOUND"),
    FORBIDDEN("BILLING_FORBIDDEN"),
    ADJUST_REASON_REQUIRED("BILLING_ADJUST_REASON_REQUIRED"),
    DUPLICATE_TRANSACTION("BILLING_DUPLICATE_TRANSACTION"),
    VALIDATION_ERROR("BILLING_VALIDATION_ERROR"),
    INTERNAL_ERROR("BILLING_INTERNAL_ERROR");

    private final String code;

    ErrorCode(String code) { this.code = code; }

    public String getCode() { return code; }
}