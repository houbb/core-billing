package io.coreplatform.billing.api.controller;

import io.coreplatform.billing.application.command.CreateAccountCommand;
import io.coreplatform.billing.api.response.PagedResponse;
import io.coreplatform.billing.api.security.RequireRole;
import io.coreplatform.billing.api.security.SecurityContext;
import io.coreplatform.billing.application.domain.Account;
import io.coreplatform.billing.application.service.AccountService;
import io.coreplatform.billing.application.service.BalanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing/accounts")
public class AccountController {

    private final AccountService accountService;
    private final BalanceService balanceService;

    public AccountController(AccountService accountService, BalanceService balanceService) {
        this.accountService = accountService;
        this.balanceService = balanceService;
    }

    /**
     * 创建账户
     */
    @PostMapping
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateAccountCommand command) {
        Account account = accountService.createAccount(command,
                SecurityContext.getUserId(), SecurityContext.getTenantId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", account.getId());
        result.put("name", account.getAccountName());
        result.put("type", account.getAccountType().name());
        result.put("status", account.getStatus().name());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * 查询账户详情
     */
    @GetMapping("/{id}")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        Account account = accountService.getAccount(id);

        Map<String, Object> result = toAccountMap(account);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询余额
     */
    @GetMapping("/{id}/balance")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable Long id) {
        Map<String, Object> balance = balanceService.getBalance(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", id);
        result.put("balance", balance.get("balance"));
        result.put("currency", balance.get("currency"));
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toAccountMap(Account account) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", account.getId());
        map.put("tenantId", account.getTenantId());
        map.put("accountName", account.getAccountName());
        map.put("accountType", account.getAccountType().name());
        map.put("status", account.getStatus().name());
        map.put("createTime", account.getCreateTime() != null ? account.getCreateTime().toString() : null);
        map.put("updateTime", account.getUpdateTime() != null ? account.getUpdateTime().toString() : null);
        return map;
    }
}