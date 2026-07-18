package io.coreplatform.billing.application.exception;

public class DuplicateTransactionException extends RuntimeException {

    private final String existingTransactionNo;

    public DuplicateTransactionException(String referenceType, String referenceId, String existingTransactionNo) {
        super("Duplicate transaction: " + referenceType + "/" + referenceId +
              " already exists as " + existingTransactionNo);
        this.existingTransactionNo = existingTransactionNo;
    }

    public String getExistingTransactionNo() { return existingTransactionNo; }
}