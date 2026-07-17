package io.coreplatform.billing.application.service;

import io.coreplatform.billing.application.command.RecordTransactionCommand;
import io.coreplatform.billing.application.domain.Account;
import io.coreplatform.billing.application.exception.AccountNotFoundException;
import io.coreplatform.billing.application.exception.BillingBusinessException;
import io.coreplatform.billing.application.port.AccountRepository;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import io.coreplatform.billing.application.support.BillingValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static io.coreplatform.billing.application.support.BillingValues.map;

@Service
public class BalanceRuntimeService {

    private final BillingRuntimeStore store;
    private final AccountRepository accountRepository;
    private final TransactionService transactionService;

    public BalanceRuntimeService(BillingRuntimeStore store,
                                 AccountRepository accountRepository,
                                 TransactionService transactionService) {
        this.store = store;
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
    }

    public List<Map<String, Object>> balances(Long accountId, String actor) {
        requireAccount(accountId);
        store.ensureBalance(accountId, "CASH", "CNY", actor);
        return store.list("balance", Map.of("account_id", accountId), 1, 100);
    }

    @Transactional
    public Map<String, Object> deposit(Long accountId, Map<String, Object> request, String actor) {
        requireActiveAccount(accountId);
        RecordTransactionCommand command = new RecordTransactionCommand();
        command.setAccountId(accountId);
        command.setType("TOP_UP");
        command.setAmount(BillingValues.positive(request, "amount"));
        command.setReferenceType("DEPOSIT");
        command.setReferenceId(BillingValues.string(
                request, "referenceId", BillingValues.number("DEP")));
        command.setDescription(BillingValues.string(request, "description", "管理员模拟充值"));
        var transaction = transactionService.recordTransaction(command, actor);
        return map(
                "transactionNo", transaction.getTransactionNo(),
                "accountId", accountId,
                "amount", transaction.getAmount(),
                "balance", balances(accountId, actor).get(0));
    }

    @Transactional
    public Map<String, Object> freeze(Map<String, Object> request, String actor) {
        Long accountId = BillingValues.longValue(request.get("accountId"));
        if (accountId == null) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_ACCOUNT_REQUIRED", "accountId 不能为空");
        }
        requireActiveAccount(accountId);
        BigDecimal amount = BillingValues.positive(request, "amount");
        String referenceId = BillingValues.requiredString(request, "referenceId");
        var existing = store.findOne("balanceReservation", Map.of("reference_id", referenceId));
        if (existing.isPresent()) {
            return existing.get();
        }

        String balanceType = BillingValues.string(request, "balanceType", "CASH").toUpperCase();
        String currency = BillingValues.string(request, "currency", "CNY").toUpperCase();
        store.ensureBalance(accountId, balanceType, currency, actor);
        if (!store.mutateBalance(accountId, balanceType, currency,
                amount.negate(), amount, actor)) {
            throw BillingBusinessException.conflict(
                    "BILLING_BALANCE_INSUFFICIENT", "可用余额不足，无法冻结");
        }

        String reservationNo = BillingValues.number("BF");
        long id = store.insert("balanceReservation", map(
                "reservation_no", reservationNo,
                "account_id", accountId,
                "balance_type", balanceType,
                "amount", amount,
                "consumed_amount", BigDecimal.ZERO,
                "currency", currency,
                "reference_id", referenceId,
                "status", "RESERVED",
                "description", BillingValues.string(request, "description", "")), actor);
        return store.findById("balanceReservation", id).orElseThrow();
    }

    @Transactional
    public Map<String, Object> consume(Map<String, Object> request, String actor) {
        String reservationNo = BillingValues.requiredString(request, "reservationNo");
        Map<String, Object> reservation = reservation(reservationNo);
        String status = BillingValues.rowString(reservation, "status");
        if ("CONSUMED".equals(status)) {
            return reservation;
        }
        if (!"RESERVED".equals(status)) {
            throw BillingBusinessException.conflict(
                    "BILLING_RESERVATION_STATE_INVALID", "余额预留状态不可确认: " + status);
        }

        BigDecimal reserved = BillingValues.rowDecimal(reservation, "amount");
        BigDecimal actual = request.get("amount") == null
                ? reserved : BillingValues.positive(request, "amount");
        if (actual.compareTo(reserved) > 0) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_CONSUME_EXCEEDS_RESERVED", "实际消费不能超过冻结金额");
        }
        Long accountId = BillingValues.rowLong(reservation, "account_id");
        BigDecimal release = reserved.subtract(actual);
        if (!store.mutateBalance(
                accountId,
                BillingValues.rowString(reservation, "balance_type"),
                BillingValues.rowString(reservation, "currency"),
                release, reserved.negate(), actor)) {
            throw BillingBusinessException.conflict(
                    "BILLING_BALANCE_CONCURRENT_CHANGE", "余额并发状态已变化，请重试");
        }

        RecordTransactionCommand command = new RecordTransactionCommand();
        command.setAccountId(accountId);
        command.setType("CONSUME");
        command.setAmount(actual);
        command.setReferenceType("BALANCE_RESERVATION");
        command.setReferenceId(reservationNo);
        command.setDescription(BillingValues.string(request, "description", "冻结余额确认扣款"));
        transactionService.recordTransaction(command, actor, false);

        store.update("balanceReservation", BillingValues.rowLong(reservation, "id"), map(
                "consumed_amount", actual,
                "status", "CONSUMED"), actor);
        return reservation(reservationNo);
    }

    @Transactional
    public Map<String, Object> release(Map<String, Object> request, String actor) {
        String reservationNo = BillingValues.requiredString(request, "reservationNo");
        Map<String, Object> reservation = reservation(reservationNo);
        String status = BillingValues.rowString(reservation, "status");
        if ("RELEASED".equals(status)) {
            return reservation;
        }
        if (!"RESERVED".equals(status)) {
            throw BillingBusinessException.conflict(
                    "BILLING_RESERVATION_STATE_INVALID", "余额预留状态不可释放: " + status);
        }
        BigDecimal amount = BillingValues.rowDecimal(reservation, "amount");
        if (!store.mutateBalance(
                BillingValues.rowLong(reservation, "account_id"),
                BillingValues.rowString(reservation, "balance_type"),
                BillingValues.rowString(reservation, "currency"),
                amount, amount.negate(), actor)) {
            throw BillingBusinessException.conflict(
                    "BILLING_BALANCE_CONCURRENT_CHANGE", "余额并发状态已变化，请重试");
        }
        store.update("balanceReservation", BillingValues.rowLong(reservation, "id"),
                Map.of("status", "RELEASED"), actor);
        return reservation(reservationNo);
    }

    public Map<String, Object> reservation(String reservationNo) {
        return store.findOne("balanceReservation", Map.of("reservation_no", reservationNo))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_RESERVATION_NOT_FOUND", "余额预留不存在: " + reservationNo));
    }

    private Account requireAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private Account requireActiveAccount(Long accountId) {
        Account account = requireAccount(accountId);
        if (!account.isActive()) {
            throw BillingBusinessException.conflict(
                    "BILLING_ACCOUNT_INACTIVE", "账户状态不是 ACTIVE");
        }
        return account;
    }
}

