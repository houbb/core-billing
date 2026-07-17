package io.coreplatform.billing.application.service;

import io.coreplatform.billing.application.exception.BillingBusinessException;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import io.coreplatform.billing.application.support.BillingValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.coreplatform.billing.application.support.BillingValues.map;

@Service
public class QuotaRuntimeService {

    private final BillingRuntimeStore store;

    public QuotaRuntimeService(BillingRuntimeStore store) {
        this.store = store;
    }

    public List<Map<String, Object>> definitions() {
        return store.list("quotaDefinition", Map.of(), 1, 1000);
    }

    @Transactional
    public Map<String, Object> createDefinition(Map<String, Object> request, String actor) {
        String resource = BillingValues.requiredString(request, "resourceCode").toUpperCase();
        store.findOne("quotaDefinition", Map.of("resource_code", resource)).ifPresent(existing -> {
            throw BillingBusinessException.conflict(
                    "BILLING_QUOTA_DEFINITION_EXISTS", "额度定义已存在: " + resource);
        });
        long id = store.insert("quotaDefinition", map(
                "resource_code", resource,
                "quota_name", BillingValues.requiredString(request, "quotaName"),
                "unit", BillingValues.requiredString(request, "unit").toUpperCase(),
                "period", BillingValues.string(request, "period", "MONTH").toUpperCase(),
                "warning_threshold", request.get("warningThreshold") == null
                        ? new BigDecimal("80") : BillingValues.decimal(request.get("warningThreshold")),
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase()), actor);
        return store.findById("quotaDefinition", id).orElseThrow();
    }

    @Transactional
    public Map<String, Object> allocate(Map<String, Object> request, String actor) {
        String tenant = BillingValues.requiredString(request, "tenantId");
        String resource = BillingValues.requiredString(request, "resourceCode").toUpperCase();
        BigDecimal total = BillingValues.positive(request, "quotaTotal");
        LocalDate start = request.get("periodStart") == null
                ? LocalDate.now().with(TemporalAdjusters.firstDayOfMonth())
                : LocalDate.parse(String.valueOf(request.get("periodStart")));
        LocalDate end = request.get("periodEnd") == null
                ? start.with(TemporalAdjusters.lastDayOfMonth())
                : LocalDate.parse(String.valueOf(request.get("periodEnd")));
        Map<String, Object> filters = map(
                "tenant_id", tenant,
                "resource_code", resource,
                "period_start", start.toString());
        var existing = store.findOne("quotaAllocation", filters);
        if (existing.isPresent()) {
            store.update("quotaAllocation", BillingValues.rowLong(existing.get(), "id"), map(
                    "quota_total", total,
                    "period_end", end.toString(),
                    "policy", BillingValues.string(request, "policy", "BLOCK").toUpperCase(),
                    "status", "ACTIVE"), actor);
            return store.findById("quotaAllocation",
                    BillingValues.rowLong(existing.get(), "id")).orElseThrow();
        }
        long id = store.insert("quotaAllocation", map(
                "tenant_id", tenant,
                "resource_code", resource,
                "quota_total", total,
                "quota_used", BigDecimal.ZERO,
                "quota_reserved", BigDecimal.ZERO,
                "period_start", start.toString(),
                "period_end", end.toString(),
                "policy", BillingValues.string(request, "policy", "BLOCK").toUpperCase(),
                "status", "ACTIVE",
                "version", 0), actor);
        return store.findById("quotaAllocation", id).orElseThrow();
    }

    public List<Map<String, Object>> check(String tenantId) {
        List<Map<String, Object>> allocations = store.list(
                "quotaAllocation", Map.of("tenant_id", tenantId, "status", "ACTIVE"), 1, 1000);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> allocation : allocations) {
            LocalDate start = LocalDate.parse(BillingValues.rowString(allocation, "period_start"));
            LocalDate end = LocalDate.parse(BillingValues.rowString(allocation, "period_end"));
            if (today.isBefore(start) || today.isAfter(end)) {
                continue;
            }
            BigDecimal total = BillingValues.rowDecimal(allocation, "quota_total");
            BigDecimal used = BillingValues.rowDecimal(allocation, "quota_used");
            BigDecimal reserved = BillingValues.rowDecimal(allocation, "quota_reserved");
            BigDecimal remaining = total.subtract(used).subtract(reserved);
            result.add(map(
                    "allocationId", BillingValues.rowLong(allocation, "id"),
                    "tenantId", tenantId,
                    "resource", BillingValues.rowString(allocation, "resource_code"),
                    "limit", total,
                    "used", used,
                    "reserved", reserved,
                    "remaining", remaining.max(BigDecimal.ZERO),
                    "overage", remaining.min(BigDecimal.ZERO).abs(),
                    "policy", BillingValues.rowString(allocation, "policy"),
                    "periodStart", start.toString(),
                    "periodEnd", end.toString()));
        }
        return result;
    }

    @Transactional
    public Map<String, Object> reserve(Map<String, Object> request, String tenantId, String actor) {
        String referenceId = BillingValues.requiredString(request, "referenceId");
        var existing = store.findOne("quotaReservation", Map.of("reference_id", referenceId));
        if (existing.isPresent()) {
            return existing.get();
        }
        String resource = BillingValues.requiredString(request, "resource").toUpperCase();
        BigDecimal amount = BillingValues.positive(request, "amount");
        Map<String, Object> allocation = currentAllocation(tenantId, resource);
        String policy = BillingValues.rowString(allocation, "policy");
        boolean enforceLimit = !"OVERAGE".equals(policy);
        boolean reserved = store.mutateQuota(
                BillingValues.rowLong(allocation, "id"),
                BigDecimal.ZERO, amount, enforceLimit, actor);
        if (!reserved) {
            if ("DEGRADE".equals(policy)) {
                throw BillingBusinessException.unprocessable(
                        "BILLING_QUOTA_DEGRADE_REQUIRED", "额度不足，需要降级执行");
            }
            throw BillingBusinessException.conflict(
                    "BILLING_QUOTA_EXCEEDED", resource + " 额度不足");
        }
        long id = store.insert("quotaReservation", map(
                "reservation_no", BillingValues.number("QR"),
                "allocation_id", BillingValues.rowLong(allocation, "id"),
                "tenant_id", tenantId,
                "resource_code", resource,
                "amount", amount,
                "committed_amount", BigDecimal.ZERO,
                "reference_id", referenceId,
                "status", "RESERVED"), actor);
        return store.findById("quotaReservation", id).orElseThrow();
    }

    @Transactional
    public Map<String, Object> commit(Map<String, Object> request, String actor) {
        Map<String, Object> reservation = reservation(
                BillingValues.requiredString(request, "reservationNo"));
        String status = BillingValues.rowString(reservation, "status");
        if ("COMMITTED".equals(status)) {
            return reservation;
        }
        if (!"RESERVED".equals(status)) {
            throw BillingBusinessException.conflict(
                    "BILLING_QUOTA_RESERVATION_STATE_INVALID", "额度预留状态不可确认: " + status);
        }
        BigDecimal reserved = BillingValues.rowDecimal(reservation, "amount");
        BigDecimal actual = request.get("amount") == null
                ? reserved : BillingValues.positive(request, "amount");
        if (actual.compareTo(reserved) > 0) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_QUOTA_COMMIT_EXCEEDS_RESERVED", "实际使用量不能超过预留额度");
        }
        Map<String, Object> allocation = store.findById(
                "quotaAllocation", BillingValues.rowLong(reservation, "allocation_id")).orElseThrow();
        boolean enforce = !"OVERAGE".equals(BillingValues.rowString(allocation, "policy"));
        if (!store.mutateQuota(
                BillingValues.rowLong(allocation, "id"), actual, reserved.negate(), enforce, actor)) {
            throw BillingBusinessException.conflict(
                    "BILLING_QUOTA_CONCURRENT_CHANGE", "额度并发状态已变化，请重试");
        }
        store.update("quotaReservation", BillingValues.rowLong(reservation, "id"), map(
                "committed_amount", actual,
                "status", "COMMITTED"), actor);
        createAlertIfNeeded(store.findById(
                "quotaAllocation", BillingValues.rowLong(allocation, "id")).orElseThrow(), actor);
        return reservation(BillingValues.rowString(reservation, "reservation_no"));
    }

    @Transactional
    public Map<String, Object> release(Map<String, Object> request, String actor) {
        Map<String, Object> reservation = reservation(
                BillingValues.requiredString(request, "reservationNo"));
        String status = BillingValues.rowString(reservation, "status");
        if ("RELEASED".equals(status)) {
            return reservation;
        }
        if (!"RESERVED".equals(status)) {
            throw BillingBusinessException.conflict(
                    "BILLING_QUOTA_RESERVATION_STATE_INVALID", "额度预留状态不可释放: " + status);
        }
        if (!store.mutateQuota(
                BillingValues.rowLong(reservation, "allocation_id"),
                BigDecimal.ZERO,
                BillingValues.rowDecimal(reservation, "amount").negate(),
                false,
                actor)) {
            throw BillingBusinessException.conflict(
                    "BILLING_QUOTA_CONCURRENT_CHANGE", "额度并发状态已变化，请重试");
        }
        store.update("quotaReservation", BillingValues.rowLong(reservation, "id"),
                Map.of("status", "RELEASED"), actor);
        return reservation(BillingValues.rowString(reservation, "reservation_no"));
    }

    @Transactional
    public Map<String, Object> consume(Map<String, Object> request, String tenantId, String actor) {
        Map<String, Object> reserved = reserve(request, tenantId, actor);
        return commit(map(
                "reservationNo", BillingValues.rowString(reserved, "reservation_no"),
                "amount", request.get("amount")), actor);
    }

    public List<Map<String, Object>> alerts(String tenantId) {
        return store.list("quotaAlert", Map.of("tenant_id", tenantId), 1, 1000);
    }

    public void authorizeReservation(String reservationNo, String tenantId, boolean superAdmin) {
        Map<String, Object> reservation = reservation(reservationNo);
        if (!superAdmin && !tenantId.equals(BillingValues.rowString(reservation, "tenant_id"))) {
            throw BillingBusinessException.forbidden(
                    "BILLING_QUOTA_TENANT_FORBIDDEN", "无权操作其他租户额度预留");
        }
    }

    private Map<String, Object> currentAllocation(String tenantId, String resource) {
        LocalDate today = LocalDate.now();
        return store.list("quotaAllocation", Map.of(
                        "tenant_id", tenantId,
                        "resource_code", resource,
                        "status", "ACTIVE"), 1, 1000).stream()
                .filter(row -> !today.isBefore(LocalDate.parse(
                        BillingValues.rowString(row, "period_start"))))
                .filter(row -> !today.isAfter(LocalDate.parse(
                        BillingValues.rowString(row, "period_end"))))
                .findFirst()
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_QUOTA_ALLOCATION_NOT_FOUND",
                        tenantId + " 未分配 " + resource + " 额度"));
    }

    private Map<String, Object> reservation(String reservationNo) {
        return store.findOne("quotaReservation", Map.of("reservation_no", reservationNo))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_QUOTA_RESERVATION_NOT_FOUND", "额度预留不存在: " + reservationNo));
    }

    private void createAlertIfNeeded(Map<String, Object> allocation, String actor) {
        BigDecimal total = BillingValues.rowDecimal(allocation, "quota_total");
        BigDecimal used = BillingValues.rowDecimal(allocation, "quota_used");
        BigDecimal percent = BillingValues.percent(used, total).multiply(new BigDecimal("100"));
        String resource = BillingValues.rowString(allocation, "resource_code");
        BigDecimal threshold = store.findOne("quotaDefinition", Map.of("resource_code", resource))
                .map(row -> BillingValues.rowDecimal(row, "warning_threshold"))
                .orElse(new BigDecimal("80"));
        String type = percent.compareTo(new BigDecimal("100")) >= 0
                ? "EXHAUSTED" : percent.compareTo(threshold) >= 0 ? "WARNING" : "";
        if (type.isBlank()) {
            return;
        }
        String tenant = BillingValues.rowString(allocation, "tenant_id");
        boolean exists = store.findOne("quotaAlert", map(
                "tenant_id", tenant,
                "resource_code", resource,
                "alert_type", type,
                "status", "OPEN")).isPresent();
        if (!exists) {
            store.insert("quotaAlert", map(
                    "tenant_id", tenant,
                    "resource_code", resource,
                    "alert_type", type,
                    "threshold", "EXHAUSTED".equals(type) ? new BigDecimal("100") : threshold,
                    "current_usage", used,
                    "status", "OPEN"), actor);
        }
    }
}
