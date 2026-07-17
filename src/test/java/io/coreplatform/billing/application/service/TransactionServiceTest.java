package io.coreplatform.billing.application.service;

import io.coreplatform.billing.application.command.RecordTransactionCommand;
import io.coreplatform.billing.application.domain.Account;
import io.coreplatform.billing.application.domain.AccountStatus;
import io.coreplatform.billing.application.domain.AccountType;
import io.coreplatform.billing.application.domain.Direction;
import io.coreplatform.billing.application.domain.Transaction;
import io.coreplatform.billing.application.domain.TransactionType;
import io.coreplatform.billing.application.exception.AccountNotFoundException;
import io.coreplatform.billing.application.port.AccountRepository;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import io.coreplatform.billing.application.port.OperationLogRepository;
import io.coreplatform.billing.application.port.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OperationLogRepository operationLogRepository;

    @Mock
    private BillingRuntimeStore runtimeStore;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository, accountRepository, operationLogRepository, runtimeStore);
        when(runtimeStore.ensureBalance(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(java.util.Map.of("amount", BigDecimal.ZERO));
        when(runtimeStore.mutateBalance(anyLong(), anyString(), anyString(),
                any(BigDecimal.class), any(BigDecimal.class), anyString())).thenReturn(true);
    }

    @Test
    void shouldRecordTopUpTransaction() {
        Account account = activeAccount(1L);

        var cmd = new RecordTransactionCommand();
        cmd.setAccountId(1L);
        cmd.setType("TOP_UP");
        cmd.setAmount(new BigDecimal("100.00"));
        cmd.setReferenceType("MANUAL");
        cmd.setReferenceId("ref001");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findByReference("MANUAL", "ref001")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            tx.setId(1L);
            return tx;
        });

        Transaction result = transactionService.recordTransaction(cmd, "user1");

        assertNotNull(result);
        assertEquals(Direction.IN, result.getDirection());
        assertEquals(new BigDecimal("100.00"), result.getAmount());
        assertEquals(TransactionType.TOP_UP, result.getTransactionType());
        assertNotNull(result.getTransactionNo());
        assertTrue(result.getTransactionNo().startsWith("TX"));
    }

    @Test
    void shouldRecordConsumeTransaction() {
        Account account = activeAccount(1L);

        var cmd = new RecordTransactionCommand();
        cmd.setAccountId(1L);
        cmd.setType("CONSUME");
        cmd.setAmount(new BigDecimal("5.00"));
        cmd.setReferenceType("AI_REQUEST");
        cmd.setReferenceId("req001");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findByReference("AI_REQUEST", "req001")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            tx.setId(2L);
            return tx;
        });

        Transaction result = transactionService.recordTransaction(cmd, "user1");

        assertEquals(Direction.OUT, result.getDirection());
        assertEquals(TransactionType.CONSUME, result.getTransactionType());
    }

    @Test
    void shouldBeIdempotent() {
        Account account = activeAccount(1L);

        var cmd = new RecordTransactionCommand();
        cmd.setAccountId(1L);
        cmd.setType("CONSUME");
        cmd.setAmount(new BigDecimal("5.00"));
        cmd.setReferenceType("AI_REQUEST");
        cmd.setReferenceId("req001");

        Transaction existingTx = new Transaction(
                1L, TransactionType.CONSUME, new BigDecimal("5.00"),
                Direction.OUT, "AI_REQUEST", "req001", "test", "user1"
        );
        existingTx.setId(10L);
        existingTx.setTransactionNo("TX202607160001");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findByReference("AI_REQUEST", "req001")).thenReturn(Optional.of(existingTx));

        Transaction result = transactionService.recordTransaction(cmd, "user1");

        // 幂等：返回已有记录，不应创建新记录
        assertEquals("TX202607160001", result.getTransactionNo());
        assertEquals(10L, result.getId());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void shouldThrowWhenAccountNotFound() {
        var cmd = new RecordTransactionCommand();
        cmd.setAccountId(999L);
        cmd.setType("CONSUME");
        cmd.setAmount(new BigDecimal("5.00"));

        when(transactionRepository.findByReference(any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> transactionService.recordTransaction(cmd, "user1"));
    }

    @Test
    void shouldThrowWhenAccountNotActive() {
        Account frozenAccount = new Account("Frozen", AccountType.PERSONAL, "t1", "u1");
        frozenAccount.setId(1L);
        frozenAccount.setStatus(AccountStatus.FROZEN);

        var cmd = new RecordTransactionCommand();
        cmd.setAccountId(1L);
        cmd.setType("CONSUME");
        cmd.setAmount(new BigDecimal("5.00"));

        when(transactionRepository.findByReference(any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(frozenAccount));

        assertThrows(IllegalStateException.class,
                () -> transactionService.recordTransaction(cmd, "user1"));
    }

    @Test
    void shouldRecordRefundTransaction() {
        Account account = activeAccount(1L);

        var cmd = new RecordTransactionCommand();
        cmd.setAccountId(1L);
        cmd.setType("REFUND");
        cmd.setAmount(new BigDecimal("10.00"));
        cmd.setReferenceType("MANUAL");
        cmd.setReferenceId("ref002");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findByReference("MANUAL", "ref002")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            tx.setId(3L);
            return tx;
        });

        Transaction result = transactionService.recordTransaction(cmd, "user1");

        assertEquals(Direction.IN, result.getDirection());
        assertEquals(TransactionType.REFUND, result.getTransactionType());
    }

    private Account activeAccount(Long id) {
        Account account = new Account("Test", AccountType.PERSONAL, "t1", "u1");
        account.setId(id);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }
}
