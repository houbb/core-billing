package io.coreplatform.billing.application.service;

import io.coreplatform.billing.application.command.CreateAccountCommand;
import io.coreplatform.billing.application.domain.Account;
import io.coreplatform.billing.application.domain.AccountType;
import io.coreplatform.billing.application.exception.AccountNotFoundException;
import io.coreplatform.billing.application.exception.BillingBusinessException;
import io.coreplatform.billing.application.port.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account createAccount(CreateAccountCommand command, String userId, String tenantId) {
        AccountType accountType;
        try {
            accountType = AccountType.valueOf(command.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的账户类型: " + command.getType() + "，支持 PERSONAL / ORGANIZATION");
        }

        Account account = new Account(
                command.getName(),
                accountType,
                tenantId != null ? tenantId : "",
                userId
        );

        Account saved = accountRepository.save(account);
        log.info("账户创建成功: id={}, name={}, type={}", saved.getId(), saved.getAccountName(), accountType);
        return saved;
    }

    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public Account getAuthorizedAccount(Long accountId, String tenantId, boolean superAdmin) {
        Account account = getAccount(accountId);
        if (!superAdmin && !account.getTenantId().equals(tenantId)) {
            throw BillingBusinessException.forbidden(
                    "BILLING_ACCOUNT_TENANT_FORBIDDEN", "无权访问其他租户账户");
        }
        return account;
    }

    public List<Account> listAccounts(int page, int size, String tenantId) {
        if (tenantId != null && !tenantId.isEmpty()) {
            return accountRepository.findByTenant(tenantId, page, size);
        }
        return accountRepository.findAll(page, size);
    }

    public int countAccounts(String tenantId) {
        return tenantId == null || tenantId.isBlank()
                ? accountRepository.count()
                : accountRepository.countByTenant(tenantId);
    }
}
