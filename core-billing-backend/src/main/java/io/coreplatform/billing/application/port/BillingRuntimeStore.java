package io.coreplatform.billing.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * P1-P9 运行时持久化端口。
 *
 * <p>Application 层只使用逻辑实体名和业务字段，不接触 JDBC/SQL。</p>
 */
public interface BillingRuntimeStore {

    long insert(String entity, Map<String, Object> values, String actor);

    Optional<Map<String, Object>> findById(String entity, Long id);

    Optional<Map<String, Object>> findOne(String entity, Map<String, Object> filters);

    List<Map<String, Object>> list(String entity, Map<String, Object> filters, int page, int size);

    int count(String entity, Map<String, Object> filters);

    int update(String entity, Long id, Map<String, Object> values, String actor);

    BigDecimal sum(String entity, String field, Map<String, Object> filters);

    Map<String, Object> ensureBalance(Long accountId, String balanceType, String currency, String actor);

    boolean mutateBalance(Long accountId, String balanceType, String currency,
                          BigDecimal availableDelta, BigDecimal frozenDelta, String actor);

    boolean mutateQuota(Long allocationId, BigDecimal usedDelta, BigDecimal reservedDelta,
                        boolean enforceLimit, String actor);

    boolean mutateBudget(Long budgetId, BigDecimal usedDelta, String actor);

    boolean incrementCouponUsage(Long couponId, String actor);
}
