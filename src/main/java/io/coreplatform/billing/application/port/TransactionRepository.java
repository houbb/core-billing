package io.coreplatform.billing.application.port;

import io.coreplatform.billing.application.domain.Transaction;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    Optional<Transaction> findById(Long id);

    Optional<Transaction> findByTransactionNo(String transactionNo);

    Optional<Transaction> findByReference(String referenceType, String referenceId);

    List<Transaction> findByAccountId(Long accountId, int page, int size);

    int countByAccountId(Long accountId);

    Transaction save(Transaction transaction);
}