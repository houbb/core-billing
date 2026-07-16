package io.coreplatform.billing.api.controller;

import io.coreplatform.billing.application.command.RecordTransactionCommand;
import io.coreplatform.billing.api.response.PagedResponse;
import io.coreplatform.billing.api.security.RequireRole;
import io.coreplatform.billing.api.security.SecurityContext;
import io.coreplatform.billing.application.domain.Transaction;
import io.coreplatform.billing.application.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * 创建交易（核心接口）
     */
    @PostMapping
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody RecordTransactionCommand command) {
        Transaction tx = transactionService.recordTransaction(command, SecurityContext.getUserId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", tx.getId());
        result.put("transactionNo", tx.getTransactionNo());
        result.put("accountId", tx.getAccountId());
        result.put("type", tx.getTransactionType().name());
        result.put("amount", tx.getAmount());
        result.put("direction", tx.getDirection().name());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * 查询交易详情
     */
    @GetMapping("/{id}")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        Transaction tx = transactionService.getTransaction(id);
        return ResponseEntity.ok(toTransactionMap(tx));
    }

    /**
     * 查询账户的流水列表
     */
    @GetMapping("/account/{accountId}")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<PagedResponse<Map<String, Object>>> listByAccount(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<Transaction> transactions = transactionService.listTransactions(accountId, page, size);
        int total = transactionService.countTransactions(accountId);

        List<Map<String, Object>> items = transactions.stream()
                .map(TransactionController::toTransactionMap)
                .toList();

        return ResponseEntity.ok(new PagedResponse<>(items, page, size, total));
    }

    /**
     * 按交易流水号查询
     */
    @GetMapping("/no/{transactionNo}")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> getByNo(@PathVariable String transactionNo) {
        Transaction tx = transactionService.getTransactionByNo(transactionNo);
        return ResponseEntity.ok(toTransactionMap(tx));
    }

    static Map<String, Object> toTransactionMap(Transaction tx) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tx.getId());
        map.put("accountId", tx.getAccountId());
        map.put("transactionNo", tx.getTransactionNo());
        map.put("transactionType", tx.getTransactionType().name());
        map.put("amount", tx.getAmount());
        map.put("direction", tx.getDirection().name());
        map.put("referenceType", tx.getReferenceType());
        map.put("referenceId", tx.getReferenceId());
        map.put("description", tx.getDescription());
        map.put("createTime", tx.getCreateTime() != null ? tx.getCreateTime().toString() : null);
        return map;
    }
}