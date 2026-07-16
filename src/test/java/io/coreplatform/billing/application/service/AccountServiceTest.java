package io.coreplatform.billing.application.service;

import io.coreplatform.billing.application.command.CreateAccountCommand;
import io.coreplatform.billing.application.command.RecordTransactionCommand;
import io.coreplatform.billing.application.domain.Account;
import io.coreplatform.billing.application.domain.AccountStatus;
import io.coreplatform.billing.application.domain.AccountType;
import io.coreplatform.billing.application.domain.Transaction;
import io.coreplatform.billing.application.exception.AccountNotFoundException;
import io.coreplatform.billing.application.port.AccountRepository;
import io.coreplatform.billing.application.port.OperationLogRepository;
import io.coreplatform.billing.application.port.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository);
    }

    @Test
    void shouldCreatePersonalAccount() {
        var cmd = new CreateAccountCommand();
        cmd.setName("Test Account");
        cmd.setType("PERSONAL");

        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        Account result = accountService.createAccount(cmd, "user1", "tenant1");

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test Account", result.getAccountName());
        assertEquals(AccountType.PERSONAL, result.getAccountType());
        assertEquals(AccountStatus.ACTIVE, result.getStatus());
    }

    @Test
    void shouldCreateOrganizationAccount() {
        var cmd = new CreateAccountCommand();
        cmd.setName("ABC Corp");
        cmd.setType("ORGANIZATION");

        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId(2L);
            return a;
        });

        Account result = accountService.createAccount(cmd, "admin1", "tenant1");

        assertEquals(AccountType.ORGANIZATION, result.getAccountType());
    }

    @Test
    void shouldThrowOnInvalidType() {
        var cmd = new CreateAccountCommand();
        cmd.setName("Bad");
        cmd.setType("INVALID_TYPE");

        assertThrows(IllegalArgumentException.class,
                () -> accountService.createAccount(cmd, "user1", "tenant1"));
    }

    @Test
    void shouldGetAccount() {
        Account account = new Account("Test", AccountType.PERSONAL, "t1", "u1");
        account.setId(1L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        Account result = accountService.getAccount(1L);
        assertEquals("Test", result.getAccountName());
    }

    @Test
    void shouldThrowWhenAccountNotFound() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> accountService.getAccount(999L));
    }
}