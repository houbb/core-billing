package io.coreplatform.billing.application.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {

    private Long id;
    private Long accountId;
    private String transactionNo;
    private TransactionType transactionType;
    private BigDecimal amount;
    private Direction direction;
    private String referenceType;
    private String referenceId;
    private String description;
    private LocalDateTime createTime;
    private String createUser;

    public Transaction() {}

    public Transaction(Long accountId, TransactionType transactionType, BigDecimal amount,
                       Direction direction, String referenceType, String referenceId,
                       String description, String createUser) {
        this.accountId = accountId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.direction = direction;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.description = description;
        this.createUser = createUser;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getTransactionNo() { return transactionNo; }
    public void setTransactionNo(String transactionNo) { this.transactionNo = transactionNo; }

    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }
}