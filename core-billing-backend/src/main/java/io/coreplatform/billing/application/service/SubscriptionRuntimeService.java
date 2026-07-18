package io.coreplatform.billing.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.billing.application.exception.BillingBusinessException;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import io.coreplatform.billing.application.support.BillingValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.coreplatform.billing.application.support.BillingValues.map;

@Service
public class SubscriptionRuntimeService {

    private final BillingRuntimeStore store;
    private final QuotaRuntimeService quotaService;
    private final ObjectMapper objectMapper;

    public SubscriptionRuntimeService(BillingRuntimeStore store,
                                      QuotaRuntimeService quotaService,
                                      ObjectMapper objectMapper) {
        this.store = store;
        this.quotaService = quotaService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> products() {
        return store.list("product", Map.of(), 1, 1000);
    }

    @Transactional
    public Map<String, Object> createProduct(Map<String, Object> request, String actor) {
        String code = BillingValues.requiredString(request, "productCode").toUpperCase();
        store.findOne("product", Map.of("product_code", code)).ifPresent(existing -> {
            throw BillingBusinessException.conflict(
                    "BILLING_PRODUCT_EXISTS", "产品已存在: " + code);
        });
        long id = store.insert("product", map(
                "product_code", code,
                "product_name", BillingValues.requiredString(request, "productName"),
                "description", BillingValues.string(request, "description", ""),
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase()), actor);
        return store.findById("product", id).orElseThrow();
    }

    public List<Map<String, Object>> plans(boolean includeDraft) {
        Map<String, Object> filters = includeDraft ? Map.of() : Map.of("status", "ACTIVE");
        List<Map<String, Object>> plans = store.list("plan", filters, 1, 1000);
        plans.forEach(this::attachPlanDetails);
        return plans;
    }

    @Transactional
    public Map<String, Object> createPlan(Map<String, Object> request, String actor) {
        Long productId = BillingValues.longValue(request.get("productId"));
        if (productId == null || store.findById("product", productId).isEmpty()) {
            throw BillingBusinessException.notFound(
                    "BILLING_PRODUCT_NOT_FOUND", "productId 对应产品不存在");
        }
        String code = BillingValues.requiredString(request, "planCode").toUpperCase();
        store.findOne("plan", Map.of("plan_code", code)).ifPresent(existing -> {
            throw BillingBusinessException.conflict(
                    "BILLING_PLAN_EXISTS", "套餐已存在: " + code);
        });
        BigDecimal price = BillingValues.decimal(request.get("price"));
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_PLAN_PRICE_INVALID", "套餐价格不能为负数");
        }
        long planId = store.insert("plan", map(
                "product_id", productId,
                "plan_code", code,
                "plan_name", BillingValues.requiredString(request, "planName"),
                "billing_cycle", BillingValues.string(request, "billingCycle", "MONTHLY").toUpperCase(),
                "price", price,
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase(),
                "current_version", 1,
                "trial_days", BillingValues.intValue(request.get("trialDays"), 0),
                "description", BillingValues.string(request, "description", "")), actor);

        for (Map<String, Object> item : items(request.get("items"))) {
            store.insert("planItem", map(
                    "plan_id", planId,
                    "item_type", BillingValues.string(item, "itemType", "QUOTA").toUpperCase(),
                    "resource_code", BillingValues.string(item, "resourceCode", "").toUpperCase(),
                    "item_code", BillingValues.string(item, "itemCode", "").toUpperCase(),
                    "item_value", BillingValues.decimal(item.get("value")),
                    "unit", BillingValues.string(item, "unit", "").toUpperCase()), actor);
        }
        createPlanVersion(planId, actor);
        return plan(planId);
    }

    @Transactional
    public Map<String, Object> publishVersion(Long planId, Map<String, Object> request, String actor) {
        Map<String, Object> plan = requirePlan(planId);
        if (request.containsKey("price")) {
            BigDecimal price = BillingValues.decimal(request.get("price"));
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                throw BillingBusinessException.unprocessable(
                        "BILLING_PLAN_PRICE_INVALID", "套餐价格不能为负数");
            }
            store.update("plan", planId, Map.of("price", price), actor);
        }
        int nextVersion = BillingValues.intValue(plan.get("current_version"), 1) + 1;
        store.update("plan", planId, map(
                "current_version", nextVersion,
                "status", "ACTIVE"), actor);
        expirePlanVersions(planId, actor);
        createPlanVersion(planId, actor);
        return plan(planId);
    }

    public Map<String, Object> plan(Long planId) {
        Map<String, Object> plan = requirePlan(planId);
        attachPlanDetails(plan);
        return plan;
    }

    @Transactional
    public Map<String, Object> subscribe(Map<String, Object> request, String tenantId, String actor) {
        String planCode = BillingValues.requiredString(request, "plan").toUpperCase();
        Map<String, Object> targetPlan = store.findOne(
                        "plan", Map.of("plan_code", planCode, "status", "ACTIVE"))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_PLAN_NOT_FOUND", "有效套餐不存在: " + planCode));
        var current = currentOptional(tenantId);
        if (current.isPresent()) {
            throw BillingBusinessException.conflict(
                    "BILLING_SUBSCRIPTION_EXISTS", "当前已有有效订阅，请使用套餐变更接口");
        }
        return createSubscription(tenantId, targetPlan, null,
                BillingValues.intValue(request.get("trialDays"),
                        BillingValues.intValue(targetPlan.get("trial_days"), 0)), actor);
    }

    public Map<String, Object> current(String tenantId) {
        Map<String, Object> subscription = currentOptional(tenantId)
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_SUBSCRIPTION_NOT_FOUND", "当前没有有效订阅"));
        subscription.put("plan", plan(BillingValues.rowLong(subscription, "plan_id")));
        return subscription;
    }

    @Transactional
    public Map<String, Object> changePlan(Map<String, Object> request, String tenantId, String actor) {
        Map<String, Object> current = current(tenantId);
        String targetCode = BillingValues.requiredString(request, "plan").toUpperCase();
        Map<String, Object> target = store.findOne(
                        "plan", Map.of("plan_code", targetCode, "status", "ACTIVE"))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_PLAN_NOT_FOUND", "目标套餐不存在: " + targetCode));
        Long currentPlanId = BillingValues.rowLong(current, "plan_id");
        if (currentPlanId.equals(BillingValues.rowLong(target, "id"))) {
            return current;
        }
        BigDecimal currentPrice = BillingValues.rowDecimal(
                requirePlan(currentPlanId), "price");
        BigDecimal targetPrice = BillingValues.rowDecimal(target, "price");
        if (targetPrice.compareTo(currentPrice) < 0) {
            validateDowngrade(tenantId, BillingValues.rowLong(target, "id"));
        }
        store.update("subscription", BillingValues.rowLong(current, "id"), map(
                "status", "CHANGED",
                "end_time", Timestamp.valueOf(LocalDateTime.now())), actor);
        return createSubscription(
                tenantId, target, currentPlanId, 0, actor);
    }

    @Transactional
    public Map<String, Object> lifecycle(String action, String tenantId, String actor) {
        Map<String, Object> current = current(tenantId);
        String normalized = action.toUpperCase();
        Map<String, Object> update = switch (normalized) {
            case "PAUSE" -> Map.of("status", "PAUSED");
            case "RESUME" -> Map.of("status", "ACTIVE");
            case "CANCEL" -> map(
                    "status", "CANCELLED",
                    "end_time", Timestamp.valueOf(LocalDateTime.now()));
            case "RENEW" -> map(
                    "status", "ACTIVE",
                    "next_billing_time", Timestamp.valueOf(nextBilling(
                            BillingValues.rowString(requirePlan(
                                    BillingValues.rowLong(current, "plan_id")), "billing_cycle"))));
            default -> throw BillingBusinessException.unprocessable(
                    "BILLING_SUBSCRIPTION_ACTION_INVALID",
                    "action 仅支持 pause / resume / cancel / renew");
        };
        store.update("subscription", BillingValues.rowLong(current, "id"), update, actor);
        return store.findById("subscription", BillingValues.rowLong(current, "id")).orElseThrow();
    }

    public List<Map<String, Object>> subscriptions(String tenantId) {
        Map<String, Object> filters = tenantId == null || tenantId.isBlank()
                ? Map.of() : Map.of("tenant_id", tenantId);
        return store.list("subscription", filters, 1, 5000);
    }

    private Map<String, Object> createSubscription(String tenantId,
                                                   Map<String, Object> plan,
                                                   Long previousPlanId,
                                                   int trialDays,
                                                   String actor) {
        LocalDateTime now = LocalDateTime.now();
        String cycle = BillingValues.rowString(plan, "billing_cycle");
        LocalDateTime nextBilling = nextBilling(cycle);
        String status = trialDays > 0 ? "TRIAL" : "ACTIVE";
        long id = store.insert("subscription", map(
                "tenant_id", tenantId,
                "plan_id", BillingValues.rowLong(plan, "id"),
                "plan_version", BillingValues.intValue(plan.get("current_version"), 1),
                "previous_plan_id", previousPlanId,
                "status", status,
                "start_time", Timestamp.valueOf(now),
                "end_time", "FREE".equals(cycle) ? null : Timestamp.valueOf(nextBilling),
                "next_billing_time", "FREE".equals(cycle) ? null : Timestamp.valueOf(nextBilling),
                "trial_end_time", trialDays > 0 ? Timestamp.valueOf(now.plusDays(trialDays)) : null,
                "cancel_at_period_end", 0), actor);
        allocatePlanQuotas(tenantId, BillingValues.rowLong(plan, "id"), actor);
        Map<String, Object> result = store.findById("subscription", id).orElseThrow();
        result.put("plan", plan(BillingValues.rowLong(plan, "id")));
        return result;
    }

    private void allocatePlanQuotas(String tenantId, Long planId, String actor) {
        for (Map<String, Object> item : store.list(
                "planItem", Map.of("plan_id", planId, "item_type", "QUOTA"), 1, 1000)) {
            quotaService.allocate(map(
                    "tenantId", tenantId,
                    "resourceCode", BillingValues.rowString(item, "resource_code"),
                    "quotaTotal", BillingValues.rowDecimal(item, "item_value"),
                    "policy", "BLOCK"), actor);
        }
    }

    private void validateDowngrade(String tenantId, Long targetPlanId) {
        Map<String, BigDecimal> targetLimits = new LinkedHashMap<>();
        for (Map<String, Object> item : store.list(
                "planItem", Map.of("plan_id", targetPlanId, "item_type", "QUOTA"), 1, 1000)) {
            targetLimits.put(BillingValues.rowString(item, "resource_code"),
                    BillingValues.rowDecimal(item, "item_value"));
        }
        for (Map<String, Object> quota : quotaService.check(tenantId)) {
            String resource = BillingValues.string(quota.get("resource"));
            BigDecimal used = BillingValues.decimal(quota.get("used"));
            BigDecimal target = targetLimits.getOrDefault(resource, BigDecimal.ZERO);
            if (used.compareTo(target) > 0) {
                throw BillingBusinessException.conflict(
                        "BILLING_DOWNGRADE_BLOCKED",
                        resource + " 当前使用量 " + used + " 超过目标套餐额度 " + target);
            }
        }
    }

    private void attachPlanDetails(Map<String, Object> plan) {
        Long planId = BillingValues.rowLong(plan, "id");
        plan.put("items", store.list("planItem", Map.of("plan_id", planId), 1, 1000));
        plan.put("versions", store.list("planVersion", Map.of("plan_id", planId), 1, 1000));
    }

    private Map<String, Object> requirePlan(Long id) {
        return store.findById("plan", id)
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_PLAN_NOT_FOUND", "套餐不存在: " + id));
    }

    private java.util.Optional<Map<String, Object>> currentOptional(String tenantId) {
        return store.list("subscription", Map.of("tenant_id", tenantId), 1, 1000).stream()
                .filter(row -> List.of("ACTIVE", "TRIAL", "PAUSED")
                        .contains(BillingValues.rowString(row, "status")))
                .findFirst();
    }

    private LocalDateTime nextBilling(String cycle) {
        LocalDateTime now = LocalDateTime.now();
        return switch (cycle) {
            case "YEARLY" -> now.plusYears(1);
            case "FREE" -> now.plusYears(100);
            default -> now.plusMonths(1);
        };
    }

    private void createPlanVersion(Long planId, String actor) {
        Map<String, Object> plan = requirePlan(planId);
        List<Map<String, Object>> planItems = store.list(
                "planItem", Map.of("plan_id", planId), 1, 1000);
        try {
            long id = store.insert("planVersion", map(
                    "plan_id", planId,
                    "version_no", BillingValues.intValue(plan.get("current_version"), 1),
                    "snapshot_json", objectMapper.writeValueAsString(map(
                            "plan", plan,
                            "items", planItems)),
                    "effective_time", Timestamp.valueOf(LocalDateTime.now()),
                    "status", "ACTIVE"), actor);
            if (id == 0) {
                throw new IllegalStateException("套餐版本创建失败");
            }
        } catch (BillingBusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_PLAN_VERSION_INVALID", "套餐版本快照无法生成");
        }
    }

    private void expirePlanVersions(Long planId, String actor) {
        for (Map<String, Object> version : store.list(
                "planVersion", Map.of("plan_id", planId, "status", "ACTIVE"), 1, 1000)) {
            store.update("planVersion", BillingValues.rowLong(version, "id"), map(
                    "status", "EXPIRED",
                    "expire_time", Timestamp.valueOf(LocalDateTime.now())), actor);
        }
    }

    private List<Map<String, Object>> items(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> raw) {
                Map<String, Object> mapped = new LinkedHashMap<>();
                raw.forEach((key, data) -> mapped.put(String.valueOf(key), data));
                result.add(mapped);
            }
        }
        return result;
    }
}

