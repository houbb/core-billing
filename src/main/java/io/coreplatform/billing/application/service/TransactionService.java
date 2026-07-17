package io.coreplatform.billing.application.service;

import io.coreplatform.billing.application.command.AdjustBalanceCommand;
import io.coreplatform.billing.application.command.RecordTransactionCommand;
import io.coreplatform.billing.application.domain.*;
import io.coreplatform.billing.application.exception.AccountNotFoundException;
import io.coreplatform.billing.application.exception.DuplicateTransactionException;
import io.coreplatform.billing.application.port.AccountRepository;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import io.coreplatform.billing.application.port.OperationLogRepository;
import io.coreplatform.billing.application.port.TransactionRepository;
import io.coreplatform.billing.application.support.BillingValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final OperationLogRepository operationLogRepository;
    private final BillingRuntimeStore runtimeStore;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              OperationLogRepository operationLogRepository,
                              BillingRuntimeStore runtimeStore) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.operationLogRepository = operationLogRepository;
        this.runtimeStore = runtimeStore;
    }

    @Transactional
    public Transaction recordTransaction(RecordTransactionCommand command, String userId) {
        return recordTransaction(command, userId, true);
    }

    @Transactional
    public Transaction recordTransaction(RecordTransactionCommand command, String userId,
                                         boolean projectBalance) {
        TransactionType txType;
        try {
            txType = TransactionType.valueOf(command.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的交易类型: " + command.getType() +
                    "，支持 TOP_UP / CONSUME / REFUND / ADJUST / PAYMENT_REFUND");
        }
        if (command.getAmount() == null || command.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("交易金额不能为 0");
        }
        if (txType != TransactionType.ADJUST && command.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("非调整交易金额必须大于 0");
        }

        // 1. 幂等检查（有 referenceType + referenceId 时才检查）
        String refType = command.getReferenceType() != null ? command.getReferenceType() : "";
        String refId = command.getReferenceId() != null ? command.getReferenceId() : "";
        if (!refType.isEmpty() && !refId.isEmpty()) {
            var existing = transactionRepository.findByReference(refType, refId);
            if (existing.isPresent()) {
                log.info("幂等命中: referenceType={}, referenceId={}, existingTxNo={}",
                        refType, refId, existing.get().getTransactionNo());
                return existing.get();
            }
        }

        // 2. 校验账户存在
        Account account = accountRepository.findById(command.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(command.getAccountId()));

        if (!account.isActive()) {
            throw new IllegalStateException("账户状态异常，无法交易: accountId=" + account.getId() +
                    ", status=" + account.getStatus());
        }

        // 3. 确定方向
        Direction direction = resolveDirection(txType, command.getAmount());
        BigDecimal ledgerAmount = command.getAmount().abs();

        if (projectBalance) {
            runtimeStore.ensureBalance(command.getAccountId(), "CASH", "CNY", userId);
        }

        // 4. 生成交易流水号
        String transactionNo = generateTransactionNo();

        // 5. 创建交易记录
        Transaction tx = new Transaction(
                command.getAccountId(),
                txType,
                ledgerAmount,
                direction,
                refType,
                refId,
                command.getDescription() != null ? command.getDescription() : "",
                userId
        );
        tx.setTransactionNo(transactionNo);

        Transaction saved = transactionRepository.save(tx);

        // 6. 同步余额投影。冻结确认场景已在 P1 状态机中调整余额，因此可关闭投影。
        if (projectBalance) {
            BigDecimal delta = direction == Direction.IN ? ledgerAmount : ledgerAmount.negate();
            boolean changed = runtimeStore.mutateBalance(
                    command.getAccountId(), "CASH", "CNY", delta, BigDecimal.ZERO, userId);
            if (!changed) {
                throw new IllegalStateException("账户可用余额不足");
            }
        }

        // 7. 记录操作日志
        operationLogRepository.save(
                String.valueOf(command.getAccountId()),
                txType.name(),
                userId,
                command.getDescription() != null ? command.getDescription() : "",
                "amount=" + command.getAmount() + ", direction=" + direction +
                ", transactionNo=" + transactionNo
        );

        log.info("交易记录创建: accountId={}, type={}, amount={}, direction={}, txNo={}",
                command.getAccountId(), txType, command.getAmount(), direction, transactionNo);
        return saved;
    }

    @Transactional
    public Transaction adjustBalance(Long accountId, AdjustBalanceCommand command, String userId) {
        // 校验账户存在
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        // 创建 ADJUST 交易
        RecordTransactionCommand txCommand = new RecordTransactionCommand();
        txCommand.setAccountId(accountId);
        txCommand.setType(TransactionType.ADJUST.name());
        txCommand.setAmount(command.getAmount());
        txCommand.setReferenceType("MANUAL_ADJUST");
        txCommand.setReferenceId("ADJ_" + System.currentTimeMillis());
        txCommand.setDescription(command.getReason());

        Transaction tx = recordTransaction(txCommand, userId);

        // 额外记录详细操作日志
        operationLogRepository.save(
                String.valueOf(accountId),
                "ADJUST",
                userId,
                command.getReason(),
                "adjustAmount=" + command.getAmount() +
                (command.getDescription() != null ? ", description=" + command.getDescription() : "")
        );

        log.info("手工调整: accountId={}, amount={}, reason={}, userId={}",
                accountId, command.getAmount(), command.getReason(), userId);
        return tx;
    }

    public Transaction getTransaction(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("交易记录不存在: id=" + id));
    }

    public Transaction getTransactionByNo(String transactionNo) {
        return transactionRepository.findByTransactionNo(transactionNo)
                .orElseThrow(() -> new IllegalArgumentException("交易记录不存在: no=" + transactionNo));
    }

    public List<Transaction> listTransactions(Long accountId, int page, int size) {
        // 校验账户存在
        if (!accountRepository.findById(accountId).isPresent()) {
            throw new AccountNotFoundException(accountId);
        }
        return transactionRepository.findByAccountId(accountId, page, size);
    }

    public int countTransactions(Long accountId) {
        return transactionRepository.countByAccountId(accountId);
    }

    public List<Transaction> listAllTransactions(int page, int size) {
        return transactionRepository.findAll(page, size);
    }

    public int countAllTransactions() {
        return transactionRepository.count();
    }

    public List<Transaction> listTenantTransactions(String tenantId, int page, int size) {
        return transactionRepository.findByTenant(tenantId, page, size);
    }

    public int countTenantTransactions(String tenantId) {
        return transactionRepository.countByTenant(tenantId);
    }

    private String generateTransactionNo() {
        return BillingValues.number("TX");
    }

    /**
     * 根据交易类型决定方向
     */
    private Direction resolveDirection(TransactionType type, BigDecimal amount) {
        return switch (type) {
            case TOP_UP -> Direction.IN;
            case CONSUME -> Direction.OUT;
            case REFUND -> Direction.IN;
            case ADJUST -> amount.compareTo(BigDecimal.ZERO) >= 0 ? Direction.IN : Direction.OUT;
            case PAYMENT_REFUND -> Direction.OUT;
        };
    }
}
