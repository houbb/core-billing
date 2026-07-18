package io.coreplatform.billing.api.controller;

import io.coreplatform.billing.application.command.AdjustBalanceCommand;
import io.coreplatform.billing.application.command.CreateAccountCommand;
import io.coreplatform.billing.api.response.PagedResponse;
import io.coreplatform.billing.api.security.RequireRole;
import io.coreplatform.billing.api.security.SecurityContext;
import io.coreplatform.billing.application.domain.Account;
import io.coreplatform.billing.application.domain.Transaction;
import io.coreplatform.billing.application.service.AccountService;
import io.coreplatform.billing.application.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing/admin")
public class AdminController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    public AdminController(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    /**
     * 管理端：创建账户
     */
    @PostMapping("/accounts")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> createAccount(@Valid @RequestBody CreateAccountCommand command) {
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
     * 管理端：账户列表
     */
    @GetMapping("/accounts")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<PagedResponse<Map<String, Object>>> listAccounts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<Account> accounts = accountService.listAccounts(page, size, SecurityContext.getTenantId());
        int total = accountService.countAccounts(SecurityContext.getTenantId());

        List<Map<String, Object>> items = accounts.stream()
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("accountName", a.getAccountName());
                    m.put("accountType", a.getAccountType().name());
                    m.put("status", a.getStatus().name());
                    m.put("createTime", a.getCreateTime() != null ? a.getCreateTime().toString() : null);
                    return m;
                })
                .toList();

        return ResponseEntity.ok(new PagedResponse<>(items, page, size, total));
    }

    @GetMapping("/transactions")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<PagedResponse<Map<String, Object>>> listTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        boolean superAdmin = SecurityContext.isSuperAdmin();
        List<Transaction> transactions = superAdmin
                ? transactionService.listAllTransactions(page, size)
                : transactionService.listTenantTransactions(
                        SecurityContext.getTenantId(), page, size);
        List<Map<String, Object>> items = transactions.stream()
                .map(TransactionController::toTransactionMap)
                .toList();
        return ResponseEntity.ok(new PagedResponse<>(
                items, page, size, superAdmin
                ? transactionService.countAllTransactions()
                : transactionService.countTenantTransactions(SecurityContext.getTenantId())));
    }

    /**
     * 管理端：手工调整余额（仅超级管理员）
     */
    @PostMapping("/accounts/{id}/adjust")
    @RequireRole({"SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> adjustBalance(
            @PathVariable Long id,
            @Valid @RequestBody AdjustBalanceCommand command) {

        Transaction tx = transactionService.adjustBalance(id, command, SecurityContext.getUserId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", tx.getId());
        result.put("transactionNo", tx.getTransactionNo());
        result.put("accountId", tx.getAccountId());
        result.put("type", tx.getTransactionType().name());
        result.put("amount", tx.getAmount());
        result.put("direction", tx.getDirection().name());
        result.put("description", tx.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
