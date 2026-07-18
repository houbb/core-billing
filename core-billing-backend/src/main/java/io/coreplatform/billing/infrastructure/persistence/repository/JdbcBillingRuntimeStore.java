package io.coreplatform.billing.infrastructure.persistence.repository;

import io.coreplatform.billing.application.port.BillingRuntimeStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JdbcBillingRuntimeStore implements BillingRuntimeStore {

    private record EntitySpec(String table, Set<String> fields) {
        private EntitySpec(String table, String... fields) {
            this(table, Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(fields))));
        }
    }

    private static final Map<String, EntitySpec> SPECS = specs();
    private static final Set<String> AUDIT_FIELDS = Set.of(
            "id", "create_time", "update_time", "create_user", "update_user");

    private final JdbcTemplate jdbc;

    public JdbcBillingRuntimeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long insert(String entity, Map<String, Object> values, String actor) {
        EntitySpec spec = spec(entity);
        Map<String, Object> safe = safeValues(spec, values);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        safe.put("create_time", now);
        safe.put("update_time", now);
        safe.put("create_user", actor);
        safe.put("update_user", actor);

        List<String> columns = new ArrayList<>(safe.keySet());
        String sql = "INSERT INTO " + spec.table() + " (" + String.join(", ", columns) + ") VALUES (" +
                columns.stream().map(column -> "?").collect(Collectors.joining(", ")) + ")";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < columns.size(); i++) {
                statement.setObject(i + 1, safe.get(columns.get(i)));
            }
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    @Override
    public Optional<Map<String, Object>> findById(String entity, Long id) {
        return findOne(entity, Map.of("id", id));
    }

    @Override
    public Optional<Map<String, Object>> findOne(String entity, Map<String, Object> filters) {
        List<Map<String, Object>> rows = list(entity, filters, 1, 1);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Map<String, Object>> list(String entity, Map<String, Object> filters, int page, int size) {
        EntitySpec spec = spec(entity);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 5000);
        List<Object> args = new ArrayList<>();
        String where = where(spec, filters, args);
        String sql = "SELECT * FROM " + spec.table() + where + " ORDER BY id DESC LIMIT ? OFFSET ?";
        args.add(safeSize);
        args.add((safePage - 1) * safeSize);
        return jdbc.queryForList(sql, args.toArray());
    }

    @Override
    public int count(String entity, Map<String, Object> filters) {
        EntitySpec spec = spec(entity);
        List<Object> args = new ArrayList<>();
        String where = where(spec, filters, args);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + spec.table() + where, Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    @Override
    public int update(String entity, Long id, Map<String, Object> values, String actor) {
        EntitySpec spec = spec(entity);
        Map<String, Object> safe = safeValues(spec, values);
        safe.remove("id");
        safe.remove("create_time");
        safe.remove("create_user");
        safe.put("update_time", Timestamp.valueOf(LocalDateTime.now()));
        safe.put("update_user", actor);
        if (safe.isEmpty()) {
            return 0;
        }
        List<String> fields = new ArrayList<>(safe.keySet());
        String setSql = fields.stream().map(field -> field + " = ?").collect(Collectors.joining(", "));
        List<Object> args = fields.stream().map(safe::get).collect(Collectors.toCollection(ArrayList::new));
        args.add(id);
        return jdbc.update("UPDATE " + spec.table() + " SET " + setSql + " WHERE id = ?", args.toArray());
    }

    @Override
    public BigDecimal sum(String entity, String field, Map<String, Object> filters) {
        EntitySpec spec = spec(entity);
        checkField(spec, field);
        List<Object> args = new ArrayList<>();
        String where = where(spec, filters, args);
        BigDecimal result = jdbc.queryForObject(
                "SELECT COALESCE(SUM(" + field + "), 0) FROM " + spec.table() + where,
                BigDecimal.class, args.toArray());
        return result == null ? BigDecimal.ZERO : result;
    }

    @Override
    public Map<String, Object> ensureBalance(Long accountId, String balanceType, String currency, String actor) {
        Map<String, Object> filters = Map.of(
                "account_id", accountId,
                "balance_type", balanceType,
                "currency", currency);
        Optional<Map<String, Object>> existing = findOne("balance", filters);
        if (existing.isPresent()) {
            return existing.get();
        }

        BigDecimal ledgerBalance = jdbc.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN direction = 'IN' THEN amount ELSE -amount END), 0) " +
                        "FROM billing_transaction WHERE account_id = ?",
                BigDecimal.class, accountId);
        try {
            insert("balance", Map.of(
                    "account_id", accountId,
                    "balance_type", balanceType,
                    "amount", ledgerBalance == null ? BigDecimal.ZERO : ledgerBalance,
                    "frozen_amount", BigDecimal.ZERO,
                    "currency", currency,
                    "version", 0), actor);
        } catch (DuplicateKeyException ignored) {
            // 并发初始化由唯一索引收敛。
        }
        return findOne("balance", filters).orElseThrow();
    }

    @Override
    public boolean mutateBalance(Long accountId, String balanceType, String currency,
                                 BigDecimal availableDelta, BigDecimal frozenDelta, String actor) {
        int updated = jdbc.update(
                "UPDATE billing_balance SET amount = amount + ?, frozen_amount = frozen_amount + ?, " +
                        "version = version + 1, update_time = ?, update_user = ? " +
                        "WHERE account_id = ? AND balance_type = ? AND currency = ? " +
                        "AND amount + ? >= 0 AND frozen_amount + ? >= 0",
                availableDelta, frozenDelta, Timestamp.valueOf(LocalDateTime.now()), actor,
                accountId, balanceType, currency, availableDelta, frozenDelta);
        return updated == 1;
    }

    @Override
    public boolean mutateQuota(Long allocationId, BigDecimal usedDelta,
                               BigDecimal reservedDelta, boolean enforceLimit, String actor) {
        String limitClause = enforceLimit
                ? " AND quota_used + ? + quota_reserved + ? <= quota_total"
                : "";
        List<Object> args = new ArrayList<>(List.of(
                usedDelta, reservedDelta, Timestamp.valueOf(LocalDateTime.now()), actor, allocationId,
                usedDelta, reservedDelta));
        if (enforceLimit) {
            args.add(usedDelta);
            args.add(reservedDelta);
        }
        int updated = jdbc.update(
                "UPDATE billing_quota_allocation SET quota_used = quota_used + ?, " +
                        "quota_reserved = quota_reserved + ?, version = version + 1, " +
                        "update_time = ?, update_user = ? WHERE id = ? " +
                        "AND quota_used + ? >= 0 AND quota_reserved + ? >= 0" + limitClause,
                args.toArray());
        return updated == 1;
    }

    @Override
    public boolean mutateBudget(Long budgetId, BigDecimal usedDelta, String actor) {
        int updated = jdbc.update(
                "UPDATE billing_budget SET used_amount = used_amount + ?, version = version + 1, " +
                        "update_time = ?, update_user = ? WHERE id = ? AND used_amount + ? >= 0",
                usedDelta, Timestamp.valueOf(LocalDateTime.now()), actor, budgetId, usedDelta);
        return updated == 1;
    }

    @Override
    public boolean incrementCouponUsage(Long couponId, String actor) {
        int updated = jdbc.update(
                "UPDATE billing_coupon SET used_count = used_count + 1, update_time = ?, update_user = ? " +
                        "WHERE id = ? AND status = 'ACTIVE' AND used_count < usage_limit " +
                        "AND (expire_time IS NULL OR expire_time > ?)",
                Timestamp.valueOf(LocalDateTime.now()), actor, couponId, Timestamp.valueOf(LocalDateTime.now()));
        return updated == 1;
    }

    private EntitySpec spec(String entity) {
        EntitySpec spec = SPECS.get(entity);
        if (spec == null) {
            throw new IllegalArgumentException("不支持的运行时实体: " + entity);
        }
        return spec;
    }

    private Map<String, Object> safeValues(EntitySpec spec, Map<String, Object> values) {
        Map<String, Object> safe = new LinkedHashMap<>();
        values.forEach((field, value) -> {
            checkField(spec, field);
            safe.put(field, value);
        });
        return safe;
    }

    private String where(EntitySpec spec, Map<String, Object> filters, List<Object> args) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        List<String> clauses = new ArrayList<>();
        filters.forEach((field, value) -> {
            checkField(spec, field);
            if (value == null) {
                clauses.add(field + " IS NULL");
            } else {
                clauses.add(field + " = ?");
                args.add(value);
            }
        });
        return " WHERE " + String.join(" AND ", clauses);
    }

    private void checkField(EntitySpec spec, String field) {
        if (!spec.fields().contains(field) && !AUDIT_FIELDS.contains(field)) {
            throw new IllegalArgumentException("实体 " + spec.table() + " 不支持字段: " + field);
        }
    }

    private static Map<String, EntitySpec> specs() {
        Map<String, EntitySpec> map = new LinkedHashMap<>();
        map.put("balance", new EntitySpec("billing_balance",
                "account_id", "balance_type", "amount", "frozen_amount", "currency", "version"));
        map.put("balanceReservation", new EntitySpec("billing_balance_reservation",
                "reservation_no", "account_id", "balance_type", "amount", "consumed_amount",
                "currency", "reference_id", "status", "description"));
        map.put("resource", new EntitySpec("billing_resource",
                "resource_code", "resource_name", "unit", "status", "description"));
        map.put("priceRule", new EntitySpec("billing_price_rule",
                "resource_code", "rule_name", "pricing_mode", "unit_quantity",
                "condition_json", "tier_json", "status"));
        map.put("priceVersion", new EntitySpec("billing_price_version",
                "rule_id", "version_no", "price", "effective_time", "expire_time", "status"));
        map.put("meter", new EntitySpec("billing_meter_definition",
                "resource_code", "meter_name", "unit", "aggregation_type", "status"));
        map.put("usageEvent", new EntitySpec("billing_usage_event",
                "event_id", "tenant_id", "account_id", "resource_code", "quantity", "unit",
                "metadata", "event_time", "status", "cost", "currency"));
        map.put("usageRecord", new EntitySpec("billing_usage_record",
                "event_id", "tenant_id", "account_id", "resource_code", "quantity", "unit",
                "period", "cost", "currency", "status"));
        map.put("usageDaily", new EntitySpec("billing_usage_daily",
                "tenant_id", "resource_code", "usage_date", "quantity", "cost", "currency"));
        map.put("quotaDefinition", new EntitySpec("billing_quota_definition",
                "resource_code", "quota_name", "unit", "period", "warning_threshold", "status"));
        map.put("quotaAllocation", new EntitySpec("billing_quota_allocation",
                "tenant_id", "resource_code", "quota_total", "quota_used", "quota_reserved",
                "period_start", "period_end", "policy", "status", "version"));
        map.put("quotaReservation", new EntitySpec("billing_quota_reservation",
                "reservation_no", "allocation_id", "tenant_id", "resource_code", "amount",
                "committed_amount", "reference_id", "status"));
        map.put("quotaAlert", new EntitySpec("billing_quota_alert",
                "tenant_id", "resource_code", "alert_type", "threshold", "current_usage", "status"));
        map.put("product", new EntitySpec("billing_product",
                "product_code", "product_name", "description", "status"));
        map.put("plan", new EntitySpec("billing_plan",
                "product_id", "plan_code", "plan_name", "billing_cycle", "price", "currency",
                "status", "current_version", "trial_days", "description"));
        map.put("planItem", new EntitySpec("billing_plan_item",
                "plan_id", "item_type", "resource_code", "item_code", "item_value", "unit"));
        map.put("planVersion", new EntitySpec("billing_plan_version",
                "plan_id", "version_no", "snapshot_json", "effective_time", "expire_time", "status"));
        map.put("subscription", new EntitySpec("billing_subscription",
                "tenant_id", "plan_id", "plan_version", "previous_plan_id", "status", "start_time",
                "end_time", "next_billing_time", "trial_end_time", "cancel_at_period_end"));
        map.put("paymentChannel", new EntitySpec("billing_payment_channel",
                "channel_code", "channel_name", "driver_code", "status", "config_ref"));
        map.put("paymentOrder", new EntitySpec("billing_payment_order",
                "order_no", "tenant_id", "business_type", "business_id", "account_id", "plan_id",
                "amount", "currency", "status", "channel_code", "idempotency_key",
                "provider_trade_no", "paid_time", "failure_reason"));
        map.put("paymentCallback", new EntitySpec("billing_payment_callback",
                "callback_id", "order_no", "channel_code", "event_type", "amount", "payload",
                "signature", "status", "processed_time", "error_message"));
        map.put("refund", new EntitySpec("billing_refund",
                "refund_no", "payment_order_id", "amount", "status", "reason", "provider_refund_no"));
        map.put("reconciliation", new EntitySpec("billing_reconciliation",
                "reconcile_date", "order_no", "local_amount", "channel_amount", "result", "detail"));
        map.put("paymentLog", new EntitySpec("billing_payment_log",
                "order_no", "operation", "detail"));
        map.put("invoice", new EntitySpec("billing_invoice",
                "invoice_no", "tenant_id", "billing_period", "currency", "subtotal", "tax",
                "discount", "total", "status", "due_time", "generated_time"));
        map.put("invoiceItem", new EntitySpec("billing_invoice_item",
                "invoice_id", "item_type", "resource_code", "description", "quantity",
                "unit_price", "amount"));
        map.put("statement", new EntitySpec("billing_statement",
                "tenant_id", "period", "opening_balance", "closing_balance", "total_in",
                "total_out", "currency"));
        map.put("settlement", new EntitySpec("billing_settlement",
                "settlement_no", "invoice_id", "payment_order_id", "amount", "status", "settled_time"));
        map.put("creditNote", new EntitySpec("billing_credit_note",
                "credit_no", "invoice_id", "amount", "reason", "status"));
        map.put("taxRule", new EntitySpec("billing_tax_rule",
                "country", "tax_type", "rate", "status"));
        map.put("revenueSnapshot", new EntitySpec("billing_revenue_snapshot",
                "snapshot_date", "period_type", "currency", "gross_revenue", "net_revenue",
                "refund_amount", "outstanding"));
        map.put("costRecord", new EntitySpec("billing_cost_record",
                "tenant_id", "resource_code", "provider", "cost", "currency", "record_date",
                "reference_type", "reference_id"));
        map.put("profitSnapshot", new EntitySpec("billing_profit_snapshot",
                "snapshot_date", "period_type", "currency", "revenue", "cost", "profit", "margin"));
        map.put("kpiSnapshot", new EntitySpec("billing_kpi_snapshot",
                "snapshot_date", "period_type", "kpi_code", "value", "dimension_type", "dimension_value"));
        map.put("forecast", new EntitySpec("billing_forecast",
                "forecast_period", "currency", "predicted_revenue", "method", "basis_json"));
        map.put("customerMetrics", new EntitySpec("billing_customer_metrics",
                "snapshot_date", "tenant_id", "mrr", "revenue", "cost", "profit", "churn_risk"));
        map.put("productMetrics", new EntitySpec("billing_product_metrics",
                "snapshot_date", "product_code", "revenue", "cost", "subscription_count",
                "conversion_rate"));
        map.put("contract", new EntitySpec("billing_contract",
                "contract_no", "tenant_id", "customer", "plan_id", "start_time", "end_time",
                "amount", "currency", "payment_term", "status", "external_sign_id"));
        map.put("organization", new EntitySpec("billing_organization_node",
                "tenant_id", "node_code", "parent_code", "node_type", "node_name", "path", "status"));
        map.put("currencyRate", new EntitySpec("billing_currency_rate",
                "from_currency", "to_currency", "rate", "effective_time", "status"));
        map.put("campaign", new EntitySpec("billing_campaign",
                "campaign_code", "campaign_name", "campaign_type", "start_time", "end_time",
                "status", "rules_json"));
        map.put("coupon", new EntitySpec("billing_coupon",
                "coupon_code", "campaign_id", "discount_type", "discount_value",
                "usage_limit", "used_count", "status", "expire_time"));
        map.put("discount", new EntitySpec("billing_discount",
                "reference_type", "reference_id", "coupon_id", "original_amount",
                "discount_amount", "final_amount", "currency"));
        map.put("listing", new EntitySpec("billing_marketplace_listing",
                "listing_code", "creator_id", "listing_name", "listing_type", "price",
                "currency", "platform_rate", "status"));
        map.put("marketplaceOrder", new EntitySpec("billing_marketplace_order",
                "order_no", "listing_id", "tenant_id", "buyer_id", "amount", "currency",
                "status", "payment_order_id"));
        map.put("partner", new EntitySpec("billing_partner",
                "partner_code", "partner_name", "partner_type", "commission_rate", "status"));
        map.put("partnerOrder", new EntitySpec("billing_partner_order",
                "order_no", "partner_id", "tenant_id", "business_type", "business_id",
                "amount", "currency", "status"));
        map.put("commission", new EntitySpec("billing_commission",
                "commission_no", "partner_id", "partner_order_id", "amount", "currency", "status"));
        map.put("budget", new EntitySpec("billing_budget",
                "tenant_id", "scope_type", "scope_id", "resource_code", "period", "budget_amount",
                "used_amount", "currency", "warning_threshold", "policy", "status", "version"));
        map.put("budgetAlert", new EntitySpec("billing_budget_alert",
                "budget_id", "alert_type", "threshold", "used_amount", "status"));
        map.put("costCenter", new EntitySpec("billing_cost_center",
                "tenant_id", "center_code", "center_name", "parent_code", "owner_id", "status"));
        map.put("costAllocation", new EntitySpec("billing_cost_allocation",
                "cost_center_id", "reference_type", "reference_id", "amount", "currency",
                "occurred_date"));
        map.put("approval", new EntitySpec("billing_approval",
                "approval_no", "tenant_id", "business_type", "business_id", "amount", "status",
                "current_step", "applicant", "reviewer", "reason", "decision_comment"));
        map.put("revenueShare", new EntitySpec("billing_revenue_share",
                "share_no", "source_type", "source_id", "beneficiary_type", "beneficiary_id",
                "gross_amount", "share_rate", "share_amount", "currency", "status"));
        map.put("payout", new EntitySpec("billing_payout",
                "payout_no", "beneficiary_type", "beneficiary_id", "amount", "currency",
                "status", "paid_time"));
        return Collections.unmodifiableMap(map);
    }
}
