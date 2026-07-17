package io.coreplatform.billing.application.service;

import io.coreplatform.billing.application.exception.AccountNotFoundException;
import io.coreplatform.billing.application.port.AccountRepository;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class BalanceService {

    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);

    private final BillingRuntimeStore runtimeStore;
    private final AccountRepository accountRepository;

    public BalanceService(BillingRuntimeStore runtimeStore, AccountRepository accountRepository) {
        this.runtimeStore = runtimeStore;
        this.accountRepository = accountRepository;
    }

    public Map<String, Object> getBalance(Long accountId) {
        if (accountRepository.findById(accountId).isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }

        Map<String, Object> balanceRow = runtimeStore.ensureBalance(
                accountId, "CASH", "CNY", "system");
        BigDecimal available = new BigDecimal(String.valueOf(balanceRow.get("amount")));
        BigDecimal frozen = new BigDecimal(String.valueOf(balanceRow.get("frozen_amount")));

        Map<String, Object> result = new HashMap<>();
        result.put("balance", available);
        result.put("available", available);
        result.put("frozen", frozen);
        result.put("total", available.add(frozen));
        result.put("currency", "CNY");

        log.debug("余额查询: accountId={}, available={}, frozen={}", accountId, available, frozen);
        return result;
    }
}
