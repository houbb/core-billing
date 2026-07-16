package io.coreplatform.billing.application.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Account {

    private Long id;
    private String tenantId;
    private String accountName;
    private AccountType accountType;
    private AccountStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createUser;
    private String updateUser;

    public Account() {}

    public Account(String accountName, AccountType accountType, String tenantId, String createUser) {
        this.tenantId = tenantId;
        this.accountName = accountName;
        this.accountType = accountType;
        this.status = AccountStatus.ACTIVE;
        this.createUser = createUser;
        this.updateUser = createUser;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getCreateUser() { return createUser; }
    public void setCreateUser(String createUser) { this.createUser = createUser; }

    public String getUpdateUser() { return updateUser; }
    public void setUpdateUser(String updateUser) { this.updateUser = updateUser; }

    public boolean isActive() {
        return AccountStatus.ACTIVE.equals(this.status);
    }
}