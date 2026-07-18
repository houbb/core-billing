package io.coreplatform.billing.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.billing.application.exception.BillingBusinessException;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import io.coreplatform.billing.application.support.BillingValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.coreplatform.billing.application.support.BillingValues.map;

@Service
public class PricingRuntimeService {

    private final BillingRuntimeStore store;
    private final ObjectMapper objectMapper;

    public PricingRuntimeService(BillingRuntimeStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> resources() {
        return store.list("resource", Map.of(), 1, 1000);
    }

    @Transactional
    public Map<String, Object> createResource(Map<String, Object> request, String actor) {
        String code = BillingValues.requiredString(request, "resourceCode").toUpperCase();
        store.findOne("resource", Map.of("resource_code", code)).ifPresent(existing -> {
            throw BillingBusinessException.conflict(
                    "BILLING_RESOURCE_EXISTS", "资源已存在: " + code);
        });
        long id = store.insert("resource", map(
                "resource_code", code,
                "resource_name", BillingValues.requiredString(request, "resourceName"),
                "unit", BillingValues.requiredString(request, "unit").toUpperCase(),
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase(),
                "description", BillingValues.string(request, "description", "")), actor);
        return store.findById("resource", id).orElseThrow();
    }

    public List<Map<String, Object>> rules(String resourceCode) {
        Map<String, Object> filters = resourceCode == null || resourceCode.isBlank()
                ? Map.of() : Map.of("resource_code", resourceCode.toUpperCase());
        List<Map<String, Object>> rules = store.list("priceRule", filters, 1, 1000);
        rules.forEach(rule -> rule.put("versions",
                store.list("priceVersion", Map.of("rule_id", BillingValues.rowLong(rule, "id")), 1, 1000)));
        return rules;
    }

    @Transactional
    public Map<String, Object> createRule(Map<String, Object> request, String actor) {
        String resourceCode = BillingValues.requiredString(request, "resourceCode").toUpperCase();
        requireResource(resourceCode);
        String mode = BillingValues.string(request, "pricingMode", "UNIT").toUpperCase();
        if (!List.of("FIXED", "UNIT", "TIERED").contains(mode)) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_PRICING_MODE_INVALID", "pricingMode 仅支持 FIXED / UNIT / TIERED");
        }
        long id = store.insert("priceRule", map(
                "resource_code", resourceCode,
                "rule_name", BillingValues.requiredString(request, "ruleName"),
                "pricing_mode", mode,
                "unit_quantity", request.get("unitQuantity") == null
                        ? BigDecimal.ONE : BillingValues.positive(request, "unitQuantity"),
                "condition_json", json(request.getOrDefault("condition", Map.of())),
                "tier_json", json(request.getOrDefault("tiers", List.of())),
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase()), actor);

        Map<String, Object> versionRequest = new LinkedHashMap<>();
        versionRequest.put("price", BillingValues.positive(request, "price"));
        versionRequest.put("effectiveTime", request.get("effectiveTime"));
        createVersion(id, versionRequest, actor);
        return rule(id);
    }

    @Transactional
    public Map<String, Object> createVersion(Long ruleId, Map<String, Object> request, String actor) {
        Map<String, Object> rule = rule(ruleId);
        int version = store.count("priceVersion", Map.of("rule_id", ruleId)) + 1;
        LocalDateTime effective = dateTime(request.get("effectiveTime"), LocalDateTime.now());
        List<Map<String, Object>> activeVersions = store.list(
                "priceVersion", Map.of("rule_id", ruleId, "status", "ACTIVE"), 1, 1000);
        for (Map<String, Object> old : activeVersions) {
            if (old.get("expire_time") == null) {
                store.update("priceVersion", BillingValues.rowLong(old, "id"),
                        map("expire_time", Timestamp.valueOf(effective), "status", "EXPIRED"), actor);
            }
        }
        long id = store.insert("priceVersion", map(
                "rule_id", ruleId,
                "version_no", version,
                "price", BillingValues.positive(request, "price"),
                "effective_time", Timestamp.valueOf(effective),
                "expire_time", request.get("expireTime") == null
                        ? null : Timestamp.valueOf(dateTime(request.get("expireTime"), null)),
                "status", "ACTIVE"), actor);
        Map<String, Object> result = store.findById("priceVersion", id).orElseThrow();
        result.put("ruleName", BillingValues.rowString(rule, "rule_name"));
        return result;
    }

    public Map<String, Object> quote(String resourceCode, Map<String, Object> context) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("resource", resourceCode);
        request.put("quantity", BigDecimal.ONE);
        request.put("context", context == null ? Map.of() : context);
        return calculate(request);
    }

    public Map<String, Object> calculate(Map<String, Object> request) {
        String resourceCode = BillingValues.requiredString(request, "resource").toUpperCase();
        BigDecimal quantity = BillingValues.positive(request, "quantity");
        Map<String, Object> context = request.get("context") instanceof Map<?, ?> raw
                ? toStringMap(raw) : Map.of();
        Map<String, Object> rule = selectRule(resourceCode, context);
        Map<String, Object> version = currentVersion(BillingValues.rowLong(rule, "id"));
        BigDecimal price = BillingValues.rowDecimal(version, "price");
        BigDecimal unit = BillingValues.rowDecimal(rule, "unit_quantity");
        String mode = BillingValues.rowString(rule, "pricing_mode");
        BigDecimal cost = switch (mode) {
            case "FIXED" -> quantity.multiply(price);
            case "TIERED" -> tiered(quantity, rule, price);
            default -> quantity.divide(unit, 12, RoundingMode.HALF_UP).multiply(price);
        };

        return map(
                "resource", resourceCode,
                "quantity", quantity,
                "unitQuantity", unit,
                "unitPrice", price,
                "cost", BillingValues.money(cost),
                "currency", "CNY",
                "pricingMode", mode,
                "rule", BillingValues.rowString(rule, "rule_name"),
                "ruleId", BillingValues.rowLong(rule, "id"),
                "version", version.get("version_no"),
                "explanation", explanation(mode, quantity, unit, price, cost));
    }

    public Map<String, Object> rule(Long id) {
        Map<String, Object> rule = store.findById("priceRule", id)
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_PRICE_RULE_NOT_FOUND", "价格规则不存在: " + id));
        rule.put("versions", store.list("priceVersion", Map.of("rule_id", id), 1, 1000));
        return rule;
    }

    private Map<String, Object> selectRule(String resourceCode, Map<String, Object> context) {
        List<Map<String, Object>> candidates = store.list(
                "priceRule", Map.of("resource_code", resourceCode, "status", "ACTIVE"), 1, 1000);
        return candidates.stream()
                .filter(rule -> matches(BillingValues.rowString(rule, "condition_json"), context))
                .max(Comparator.comparingInt(rule ->
                        parseMap(BillingValues.rowString(rule, "condition_json")).size()))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_PRICE_NOT_FOUND", "没有匹配的有效价格: " + resourceCode));
    }

    private Map<String, Object> currentVersion(Long ruleId) {
        LocalDateTime now = LocalDateTime.now();
        return store.list("priceVersion", Map.of("rule_id", ruleId), 1, 1000).stream()
                .filter(version -> !"DISABLED".equals(BillingValues.rowString(version, "status")))
                .filter(version -> !toDateTime(version.get("effective_time")).isAfter(now))
                .filter(version -> version.get("expire_time") == null ||
                        toDateTime(version.get("expire_time")).isAfter(now))
                .max(Comparator.comparingInt(version ->
                        BillingValues.intValue(version.get("version_no"), 0)))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_PRICE_VERSION_NOT_FOUND", "价格规则没有当前生效版本: " + ruleId));
    }

    private BigDecimal tiered(BigDecimal quantity, Map<String, Object> rule, BigDecimal fallbackPrice) {
        List<Map<String, Object>> tiers = parseList(BillingValues.rowString(rule, "tier_json"));
        if (tiers.isEmpty()) {
            return quantity.multiply(fallbackPrice);
        }
        BigDecimal remaining = quantity;
        BigDecimal lower = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> tier : tiers) {
            BigDecimal price = BillingValues.decimal(tier.getOrDefault("price", fallbackPrice));
            Object upToValue = tier.get("upTo");
            BigDecimal units;
            if (upToValue == null || BillingValues.decimal(upToValue).compareTo(BigDecimal.ZERO) <= 0) {
                units = remaining;
            } else {
                BigDecimal upper = BillingValues.decimal(upToValue);
                BigDecimal capacity = upper.subtract(lower).max(BigDecimal.ZERO);
                units = remaining.min(capacity);
                lower = upper;
            }
            total = total.add(units.multiply(price));
            remaining = remaining.subtract(units);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            total = total.add(remaining.multiply(fallbackPrice));
        }
        return total;
    }

    private boolean matches(String conditionJson, Map<String, Object> context) {
        Map<String, Object> conditions = parseMap(conditionJson);
        return conditions.entrySet().stream()
                .allMatch(entry -> String.valueOf(entry.getValue())
                        .equalsIgnoreCase(String.valueOf(context.get(entry.getKey()))));
    }

    private String explanation(String mode, BigDecimal quantity, BigDecimal unit,
                               BigDecimal price, BigDecimal cost) {
        return switch (mode) {
            case "FIXED" -> quantity + " × " + price + " = " + BillingValues.money(cost);
            case "TIERED" -> "按阶梯累计计算 " + quantity + " = " + BillingValues.money(cost);
            default -> quantity + " / " + unit + " × " + price + " = " + BillingValues.money(cost);
        };
    }

    private void requireResource(String resourceCode) {
        store.findOne("resource", Map.of("resource_code", resourceCode))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_RESOURCE_NOT_FOUND", "资源不存在: " + resourceCode));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_JSON_INVALID", "JSON 数据无法序列化");
        }
    }

    private Map<String, Object> parseMap(String value) {
        try {
            if (value == null || value.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_PRICE_CONDITION_INVALID", "价格条件 JSON 无效");
        }
    }

    private List<Map<String, Object>> parseList(String value) {
        try {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_PRICE_TIER_INVALID", "阶梯价格 JSON 无效");
        }
    }

    private Map<String, Object> toStringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private LocalDateTime dateTime(Object value, LocalDateTime defaultValue) {
        if (value == null || BillingValues.string(value).isBlank()) {
            return defaultValue;
        }
        return toDateTime(value);
    }

    private LocalDateTime toDateTime(Object value) {
        return BillingValues.dateTime(value);
    }
}
