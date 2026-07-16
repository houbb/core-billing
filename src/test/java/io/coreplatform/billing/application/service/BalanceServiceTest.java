package io.coreplatform.billing.application.service;

import io.coreplatform.billing.application.domain.Account;
import io.coreplatform.billing.application.domain.AccountStatus;
import io.coreplatform.billing.application.domain.AccountType;
import io.coreplatform.billing.application.exception.AccountNotFoundException;
import io.coreplatform.billing.application.port.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private AccountRepository accountRepository;

    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        balanceService = new BalanceService(jdbc, accountRepository);
    }

    @Test
    void shouldCalculateBalance() {
        Account account = activeAccount(1L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(jdbc.queryForObject(
                eq("SELECT COALESCE(SUM(amount), 0) FROM billing_transaction WHERE account_id = ? AND direction = 'IN'"),
                eq(BigDecimal.class), eq(1L)))
                .thenReturn(new BigDecimal("200.00"));
        when(jdbc.queryForObject(
                eq("SELECT COALESCE(SUM(amount), 0) FROM billing_transaction WHERE account_id = ? AND direction = 'OUT'"),
                eq(BigDecimal.class), eq(1L)))
                .thenReturn(new BigDecimal("50.00"));

        Map<String, Object> result = balanceService.getBalance(1L);

        assertEquals(new BigDecimal("150.00"), result.get("balance"));
        assertEquals("CNY", result.get("currency"));
        assertEquals(new BigDecimal("200.00"), result.get("totalIn"));
        assertEquals(new BigDecimal("50.00"), result.get("totalOut"));
    }

    @Test
    void shouldReturnZeroWhenNoTransactions() {
        Account account = activeAccount(1L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(jdbc.queryForObject(any(String.class), eq(BigDecimal.class), eq(1L)))
                .thenReturn(BigDecimal.ZERO);

        Map<String, Object> result = balanceService.getBalance(1L);

        assertEquals(BigDecimal.ZERO, result.get("balance"));
    }

    @Test
    void shouldThrowWhenAccountNotFound() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> balanceService.getBalance(999L));
    }

    @Test
    void shouldHandleNegativeBalance() {
        Account account = activeAccount(1L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(jdbc.queryForObject(
                eq("SELECT COALESCE(SUM(amount), 0) FROM billing_transaction WHERE account_id = ? AND direction = 'IN'"),
                eq(BigDecimal.class), eq(1L)))
                .thenReturn(new BigDecimal("50.00"));
        when(jdbc.queryForObject(
                eq("SELECT COALESCE(SUM(amount), 0) FROM billing_transaction WHERE account_id = ? AND direction = 'OUT'"),
                eq(BigDecimal.class), eq(1L)))
                .thenReturn(new BigDecimal("150.00"));

        Map<String, Object> result = balanceService.getBalance(1L);

        // 负余额，允许（MVP 策略）
        assertEquals(new BigDecimal("-100.00"), result.get("balance"));
    }

    private Account activeAccount(Long id) {
        Account account = new Account("Test", AccountType.PERSONAL, "t1", "u1");
        account.setId(id);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }
}