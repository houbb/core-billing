package io.coreplatform.billing.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.billing.application.exception.BillingBusinessException;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import io.coreplatform.billing.application.support.BillingValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.coreplatform.billing.application.support.BillingValues.map;

@Service
public class FinanceRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(FinanceRuntimeService.class);

    private final BillingRuntimeStore store;
    private final ObjectMapper objectMapper;

    public FinanceRuntimeService(BillingRuntimeStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> addCost(Map<String, Object> request,
                                       String defaultTenant,
                                       String actor) {
        long id = store.insert("costRecord", map(
                "tenant_id", BillingValues.string(request, "tenantId", defaultTenant),
                "resource_code", BillingValues.requiredString(request, "resourceCode").toUpperCase(),
                "provider", BillingValues.requiredString(request, "provider"),
                "cost", BillingValues.positive(request, "cost"),
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "record_date", BillingValues.string(request, "recordDate", BillingValues.today()),
                "reference_type", BillingValues.string(request, "referenceType", ""),
                "reference_id", BillingValues.string(request, "referenceId", "")), actor);
        return store.findById("costRecord", id).orElseThrow();
    }

    @Transactional
    public Map<String, Object> snapshot(String date, String periodType, String actor) {
        LocalDate target = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        String normalizedPeriod = periodType == null || periodType.isBlank()
                ? "DAY" : periodType.toUpperCase();
        if (!List.of("DAY", "MONTH").contains(normalizedPeriod)) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_FINANCE_PERIOD_INVALID", "periodType 仅支持 DAY / MONTH");
        }

        List<Map<String, Object>> successfulOrders = store.list(
                "paymentOrder", Map.of(), 1, 5000).stream()
                .filter(order -> List.of("SUCCESS", "REFUNDED")
                        .contains(BillingValues.rowString(order, "status")))
                .filter(order -> inPeriod(order.get("paid_time"), target, normalizedPeriod))
                .toList();
        BigDecimal gross = successfulOrders.stream()
                .map(order -> BillingValues.rowDecimal(order, "amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunds = store.list("refund", Map.of("status", "SUCCESS"), 1, 5000).stream()
                .filter(refund -> inPeriod(refund.get("update_time"), target, normalizedPeriod))
                .map(refund -> BillingValues.rowDecimal(refund, "amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = store.list("invoice", Map.of(), 1, 5000).stream()
                .filter(invoice -> List.of("GENERATED", "SENT", "OVERDUE")
                        .contains(BillingValues.rowString(invoice, "status")))
                .filter(invoice -> invoicePeriodMatches(
                        BillingValues.rowString(invoice, "billing_period"), target, normalizedPeriod))
                .map(invoice -> BillingValues.rowDecimal(invoice, "total"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = gross.subtract(refunds);
        BigDecimal cost = store.list("costRecord", Map.of(), 1, 5000).stream()
                .filter(record -> recordDateMatches(
                        BillingValues.rowString(record, "record_date"), target, normalizedPeriod))
                .map(record -> BillingValues.rowDecimal(record, "cost"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profit = net.subtract(cost);
        BigDecimal margin = BillingValues.percent(profit, net);
        String snapshotDate = normalizedPeriod.equals("MONTH")
                ? YearMonth.from(target).atEndOfMonth().toString() : target.toString();

        Map<String, Object> revenue = upsert("revenueSnapshot", map(
                        "snapshot_date", snapshotDate,
                        "period_type", normalizedPeriod,
                        "currency", "CNY"),
                map(
                        "snapshot_date", snapshotDate,
                        "period_type", normalizedPeriod,
                        "currency", "CNY",
                        "gross_revenue", gross,
                        "net_revenue", net,
                        "refund_amount", refunds,
                        "outstanding", outstanding), actor);
        Map<String, Object> profitRow = upsert("profitSnapshot", map(
                        "snapshot_date", snapshotDate,
                        "period_type", normalizedPeriod,
                        "currency", "CNY"),
                map(
                        "snapshot_date", snapshotDate,
                        "period_type", normalizedPeriod,
                        "currency", "CNY",
                        "revenue", net,
                        "cost", cost,
                        "profit", profit,
                        "margin", margin), actor);

        Map<String, BigDecimal> kpis = calculateKpis(net, gross, refunds, margin);
        kpis.forEach((code, value) -> upsert("kpiSnapshot", map(
                        "snapshot_date", snapshotDate,
                        "period_type", normalizedPeriod,
                        "kpi_code", code,
                        "dimension_type", "GLOBAL",
                        "dimension_value", ""),
                map(
                        "snapshot_date", snapshotDate,
                        "period_type", normalizedPeriod,
                        "kpi_code", code,
                        "value", value,
                        "dimension_type", "GLOBAL",
                        "dimension_value", ""), actor));
        buildCustomerMetrics(snapshotDate, successfulOrders, actor);
        buildProductMetrics(snapshotDate, successfulOrders, actor);
        Map<String, Object> forecast = buildForecast(target, actor);
        return map(
                "revenue", revenue,
                "profit", profitRow,
                "kpis", kpis,
                "forecast", forecast);
    }

    public Map<String, Object> dashboard() {
        List<Map<String, Object>> revenue = store.list("revenueSnapshot", Map.of(), 1, 365);
        List<Map<String, Object>> profit = store.list("profitSnapshot", Map.of(), 1, 365);
        List<Map<String, Object>> kpis = store.list("kpiSnapshot", Map.of(), 1, 1000);
        List<Map<String, Object>> customers = store.list("customerMetrics", Map.of(), 1, 100);
        List<Map<String, Object>> products = store.list("productMetrics", Map.of(), 1, 100);
        List<Map<String, Object>> forecasts = store.list("forecast", Map.of(), 1, 24);
        return map(
                "latestRevenue", revenue.isEmpty() ? Map.of() : revenue.get(0),
                "latestProfit", profit.isEmpty() ? Map.of() : profit.get(0),
                "kpis", latestKpis(kpis),
                "revenueTrend", revenue,
                "profitTrend", profit,
                "customers", customers,
                "products", products,
                "forecasts", forecasts);
    }

    public List<Map<String, Object>> customers() {
        return store.list("customerMetrics", Map.of(), 1, 5000);
    }

    public List<Map<String, Object>> products() {
        return store.list("productMetrics", Map.of(), 1, 5000);
    }

    public List<Map<String, Object>> forecasts() {
        return store.list("forecast", Map.of(), 1, 5000);
    }

    @Scheduled(cron = "${core.billing.finance.snapshot-cron:0 20 2 * * *}")
    public void scheduledSnapshot() {
        try {
            snapshot(LocalDate.now().minusDays(1).toString(), "DAY", "finance-job");
            if (LocalDate.now().getDayOfMonth() == 1) {
                LocalDate previous = LocalDate.now().minusMonths(1);
                snapshot(previous.toString(), "MONTH", "finance-job");
            }
        } catch (Exception exception) {
            log.error("财务快照任务失败", exception);
        }
    }

    private Map<String, BigDecimal> calculateKpis(BigDecimal net,
                                                   BigDecimal gross,
                                                   BigDecimal refunds,
                                                   BigDecimal margin) {
        List<Map<String, Object>> activeSubscriptions = store.list(
                "subscription", Map.of(), 1, 5000).stream()
                .filter(row -> List.of("ACTIVE", "TRIAL")
                        .contains(BillingValues.rowString(row, "status")))
                .toList();
        BigDecimal mrr = activeSubscriptions.stream()
                .filter(row -> "ACTIVE".equals(BillingValues.rowString(row, "status")))
                .map(row -> {
                    Map<String, Object> plan = store.findById(
                            "plan", BillingValues.rowLong(row, "plan_id")).orElseThrow();
                    BigDecimal price = BillingValues.rowDecimal(plan, "price");
                    return "YEARLY".equals(BillingValues.rowString(plan, "billing_cycle"))
                            ? price.divide(new BigDecimal("12"), 6, RoundingMode.HALF_UP)
                            : price;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Set<String> activeCustomers = new LinkedHashSet<>();
        Set<String> payingCustomers = new LinkedHashSet<>();
        Set<String> trialCustomers = new LinkedHashSet<>();
        for (Map<String, Object> subscription : activeSubscriptions) {
            String tenant = BillingValues.rowString(subscription, "tenant_id");
            activeCustomers.add(tenant);
            if ("TRIAL".equals(BillingValues.rowString(subscription, "status"))) {
                trialCustomers.add(tenant);
            } else {
                payingCustomers.add(tenant);
            }
        }
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        result.put("REVENUE", net);
        result.put("MRR", mrr);
        result.put("ARR", mrr.multiply(new BigDecimal("12")));
        result.put("ACTIVE_CUSTOMER", BigDecimal.valueOf(activeCustomers.size()));
        result.put("PAYING_CUSTOMER", BigDecimal.valueOf(payingCustomers.size()));
        result.put("TRIAL_CUSTOMER", BigDecimal.valueOf(trialCustomers.size()));
        result.put("ARPU", activeCustomers.isEmpty() ? BigDecimal.ZERO
                : net.divide(BigDecimal.valueOf(activeCustomers.size()), 6, RoundingMode.HALF_UP));
        result.put("REFUND_RATE", BillingValues.percent(refunds, gross));
        result.put("GROSS_MARGIN", margin);
        return result;
    }

    private void buildCustomerMetrics(String snapshotDate,
                                      List<Map<String, Object>> successfulOrders,
                                      String actor) {
        Set<String> tenants = new LinkedHashSet<>();
        successfulOrders.forEach(order -> tenants.add(BillingValues.rowString(order, "tenant_id")));
        store.list("subscription", Map.of(), 1, 5000)
                .forEach(subscription -> tenants.add(BillingValues.rowString(subscription, "tenant_id")));
        for (String tenant : tenants) {
            BigDecimal revenue = successfulOrders.stream()
                    .filter(order -> tenant.equals(BillingValues.rowString(order, "tenant_id")))
                    .map(order -> BillingValues.rowDecimal(order, "amount"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cost = store.list("costRecord", Map.of("tenant_id", tenant), 1, 5000).stream()
                    .filter(record -> snapshotDate.equals(BillingValues.rowString(record, "record_date")))
                    .map(record -> BillingValues.rowDecimal(record, "cost"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal mrr = store.list("subscription", Map.of("tenant_id", tenant), 1, 100).stream()
                    .filter(subscription -> "ACTIVE".equals(
                            BillingValues.rowString(subscription, "status")))
                    .map(subscription -> store.findById(
                            "plan", BillingValues.rowLong(subscription, "plan_id")).orElseThrow())
                    .map(plan -> BillingValues.rowDecimal(plan, "price"))
                    .findFirst().orElse(BigDecimal.ZERO);
            BigDecimal churnRisk = mrr.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ONE : BigDecimal.ZERO;
            upsert("customerMetrics", map(
                            "snapshot_date", snapshotDate,
                            "tenant_id", tenant),
                    map(
                            "snapshot_date", snapshotDate,
                            "tenant_id", tenant,
                            "mrr", mrr,
                            "revenue", revenue,
                            "cost", cost,
                            "profit", revenue.subtract(cost),
                            "churn_risk", churnRisk), actor);
        }
    }

    private void buildProductMetrics(String snapshotDate,
                                     List<Map<String, Object>> successfulOrders,
                                     String actor) {
        for (Map<String, Object> product : store.list("product", Map.of(), 1, 1000)) {
            Long productId = BillingValues.rowLong(product, "id");
            List<Map<String, Object>> plans = store.list(
                    "plan", Map.of("product_id", productId), 1, 1000);
            Set<Long> planIds = plans.stream()
                    .map(plan -> BillingValues.rowLong(plan, "id"))
                    .collect(java.util.stream.Collectors.toSet());
            BigDecimal revenue = successfulOrders.stream()
                    .filter(order -> BillingValues.rowLong(order, "plan_id") != null)
                    .filter(order -> planIds.contains(BillingValues.rowLong(order, "plan_id")))
                    .map(order -> BillingValues.rowDecimal(order, "amount"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int subscriptions = store.list("subscription", Map.of(), 1, 5000).stream()
                    .filter(subscription -> planIds.contains(
                            BillingValues.rowLong(subscription, "plan_id")))
                    .toList().size();
            int trials = store.list("subscription", Map.of("status", "TRIAL"), 1, 5000).stream()
                    .filter(subscription -> planIds.contains(
                            BillingValues.rowLong(subscription, "plan_id")))
                    .toList().size();
            BigDecimal conversion = trials == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(subscriptions - trials)
                    .divide(BigDecimal.valueOf(trials), 6, RoundingMode.HALF_UP);
            upsert("productMetrics", map(
                            "snapshot_date", snapshotDate,
                            "product_code", BillingValues.rowString(product, "product_code")),
                    map(
                            "snapshot_date", snapshotDate,
                            "product_code", BillingValues.rowString(product, "product_code"),
                            "revenue", revenue,
                            "cost", BigDecimal.ZERO,
                            "subscription_count", subscriptions,
                            "conversion_rate", conversion), actor);
        }
    }

    private Map<String, Object> buildForecast(LocalDate target, String actor) {
        YearMonth next = YearMonth.from(target).plusMonths(1);
        List<Map<String, Object>> monthly = store.list(
                "revenueSnapshot", Map.of("period_type", "MONTH"), 1, 12);
        BigDecimal prediction;
        if (monthly.isEmpty()) {
            prediction = calculateKpis(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO).get("MRR");
        } else {
            List<Map<String, Object>> recent = monthly.stream().limit(3).toList();
            prediction = recent.stream()
                    .map(row -> BillingValues.rowDecimal(row, "net_revenue"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(recent.size()), 6, RoundingMode.HALF_UP);
            if (recent.size() >= 2) {
                BigDecimal newest = BillingValues.rowDecimal(recent.get(0), "net_revenue");
                BigDecimal oldest = BillingValues.rowDecimal(recent.get(recent.size() - 1), "net_revenue");
                prediction = prediction.add(
                        newest.subtract(oldest).divide(
                                BigDecimal.valueOf(recent.size() - 1), 6, RoundingMode.HALF_UP));
            }
        }
        String basis;
        try {
            basis = objectMapper.writeValueAsString(monthly.stream().limit(3).toList());
        } catch (Exception exception) {
            basis = "[]";
        }
        return upsert("forecast", map(
                        "forecast_period", next.toString(),
                        "currency", "CNY"),
                map(
                        "forecast_period", next.toString(),
                        "currency", "CNY",
                        "predicted_revenue", prediction.max(BigDecimal.ZERO),
                        "method", "LINEAR",
                        "basis_json", basis), actor);
    }

    private Map<String, Object> upsert(String entity,
                                       Map<String, Object> filters,
                                       Map<String, Object> values,
                                       String actor) {
        var existing = store.findOne(entity, filters);
        if (existing.isPresent()) {
            Long id = BillingValues.rowLong(existing.get(), "id");
            store.update(entity, id, values, actor);
            return store.findById(entity, id).orElseThrow();
        }
        long id = store.insert(entity, values, actor);
        return store.findById(entity, id).orElseThrow();
    }

    private Map<String, Object> latestKpis(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        rows.stream()
                .sorted(Comparator.comparing(
                        row -> BillingValues.rowString(row, "snapshot_date"),
                        Comparator.reverseOrder()))
                .forEach(row -> result.putIfAbsent(
                        BillingValues.rowString(row, "kpi_code"), row));
        return new LinkedHashMap<>(result);
    }

    private boolean inPeriod(Object value, LocalDate target, String periodType) {
        if (value == null) {
            return false;
        }
        LocalDate date = toDateTime(value).toLocalDate();
        return "MONTH".equals(periodType)
                ? YearMonth.from(date).equals(YearMonth.from(target))
                : date.equals(target);
    }

    private boolean recordDateMatches(String value, LocalDate target, String periodType) {
        LocalDate date = LocalDate.parse(value);
        return "MONTH".equals(periodType)
                ? YearMonth.from(date).equals(YearMonth.from(target))
                : date.equals(target);
    }

    private boolean invoicePeriodMatches(String value, LocalDate target, String periodType) {
        return "MONTH".equals(periodType)
                ? value.equals(YearMonth.from(target).toString())
                : value.equals(YearMonth.from(target).toString());
    }

    private LocalDateTime toDateTime(Object value) {
        return BillingValues.dateTime(value);
    }
}
