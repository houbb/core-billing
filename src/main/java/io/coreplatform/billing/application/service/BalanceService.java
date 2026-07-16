package io.coreplatform.billing.application.service;

import io.coreplatform.billing.application.exception.AccountNotFoundException;
import io.coreplatform.billing.application.port.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class BalanceService {

    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);

    private final JdbcTemplate jdbc;
    private final AccountRepository accountRepository;

    public BalanceService(JdbcTemplate jdbc, AccountRepository accountRepository) {
        this.jdbc = jdbc;
        this.accountRepository = accountRepository;
    }

    /**
     * 实时计算账户余额: SUM(IN) - SUM(OUT)
     */
    public Map<String, Object> getBalance(Long accountId) {
        // 校验账户存在
        if (accountRepository.findById(accountId).isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }

        // IN 汇总
        String inSql = "SELECT COALESCE(SUM(amount), 0) FROM billing_transaction " +
                       "WHERE account_id = ? AND direction = 'IN'";
        BigDecimal totalIn = jdbc.queryForObject(inSql, BigDecimal.class, accountId);
        if (totalIn == null) totalIn = BigDecimal.ZERO;

        // OUT 汇总
        String outSql = "SELECT COALESCE(SUM(amount), 0) FROM billing_transaction " +
                        "WHERE account_id = ? AND direction = 'OUT'";
        BigDecimal totalOut = jdbc.queryForObject(outSql, BigDecimal.class, accountId);
        if (totalOut == null) totalOut = BigDecimal.ZERO;

        BigDecimal balance = totalIn.subtract(totalOut);

        Map<String, Object> result = new HashMap<>();
        result.put("balance", balance);
        result.put("currency", "CNY");
        result.put("totalIn", totalIn);
        result.put("totalOut", totalOut);

        log.debug("余额查询: accountId={}, balance={}", accountId, balance);
        return result;
    }
}