package io.coreplatform.billing.application.port;

import io.coreplatform.billing.infrastructure.persistence.entity.BillingOperationLogEntity;

import java.util.List;

public interface OperationLogRepository {

    void save(String accountId, String operation, String operator, String reason, String detail);

    List<BillingOperationLogEntity> findByAccountId(Long accountId, int page, int size);
}