package io.coreplatform.billing.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.billing.application.command.RecordTransactionCommand;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.coreplatform.billing.application.support.BillingValues.map;

@Service
public class MeteringRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(MeteringRuntimeService.class);

    private final BillingRuntimeStore store;
    private final PricingRuntimeService pricingService;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    public MeteringRuntimeService(BillingRuntimeStore store,
                                  PricingRuntimeService pricingService,
                                  TransactionService transactionService,
                                  ObjectMapper objectMapper) {
        this.store = store;
        this.pricingService = pricingService;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> meters() {
        return store.list("meter", Map.of(), 1, 1000);
    }

    @Transactional
    public Map<String, Object> createMeter(Map<String, Object> request, String actor) {
        String resource = BillingValues.requiredString(request, "resourceCode").toUpperCase();
        store.findOne("meter", Map.of("resource_code", resource)).ifPresent(existing -> {
            throw BillingBusinessException.conflict(
                    "BILLING_METER_EXISTS", "计量器已存在: " + resource);
        });
        String aggregation = BillingValues.string(
                request, "aggregationType", "SUM").toUpperCase();
        if (!List.of("SUM", "COUNT", "MAX", "AVERAGE").contains(aggregation)) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_AGGREGATION_INVALID", "aggregationType 不受支持");
        }
        long id = store.insert("meter", map(
                "resource_code", resource,
                "meter_name", BillingValues.requiredString(request, "meterName"),
                "unit", BillingValues.requiredString(request, "unit").toUpperCase(),
                "aggregation_type", aggregation,
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase()), actor);
        return store.findById("meter", id).orElseThrow();
    }

    @Transactional
    public Map<String, Object> ingest(Map<String, Object> request, String tenantId, String actor) {
        String eventId = BillingValues.requiredString(request, "eventId");
        var existing = store.findOne("usageEvent", Map.of("event_id", eventId));
        if (existing.isPresent()) {
            return usageEnvelope(existing.get());
        }
        String resource = BillingValues.requiredString(request, "resource").toUpperCase();
        Map<String, Object> meter = store.findOne("meter", Map.of(
                        "resource_code", resource, "status", "ACTIVE"))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_METER_NOT_FOUND", "计量器不存在: " + resource));
        BigDecimal quantity = BillingValues.positive(request, "quantity");
        String unit = BillingValues.string(
                request, "unit", BillingValues.rowString(meter, "unit")).toUpperCase();
        if (!unit.equalsIgnoreCase(BillingValues.rowString(meter, "unit"))) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_USAGE_UNIT_MISMATCH", "上报单位与计量器不一致");
        }
        Map<String, Object> context = request.get("metadata") instanceof Map<?, ?> metadata
                ? stringMap(metadata) : Map.of();
        BigDecimal cost = BigDecimal.ZERO;
        String status = "PENDING";
        try {
            Map<String, Object> calculation = pricingService.calculate(map(
                    "resource", resource,
                    "quantity", quantity,
                    "context", context));
            cost = BillingValues.decimal(calculation.get("cost"));
            status = "CALCULATED";
        } catch (BillingBusinessException exception) {
            if (!"BILLING_PRICE_NOT_FOUND".equals(exception.getErrorCode())) {
                throw exception;
            }
            log.info("用量暂未计价: eventId={}, resource={}", eventId, resource);
        }

        Long accountId = BillingValues.longValue(request.get("accountId"));
        String eventTenant = BillingValues.string(request, "tenantId", tenantId);
        LocalDateTime eventTime = request.get("eventTime") == null
                ? LocalDateTime.now() : parseDateTime(request.get("eventTime"));
        long eventPk = store.insert("usageEvent", map(
                "event_id", eventId,
                "tenant_id", eventTenant,
                "account_id", accountId,
                "resource_code", resource,
                "quantity", quantity,
                "unit", unit,
                "metadata", json(context),
                "event_time", Timestamp.valueOf(eventTime),
                "status", status,
                "cost", cost,
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase()), actor);
        store.insert("usageRecord", map(
                "event_id", eventId,
                "tenant_id", eventTenant,
                "account_id", accountId,
                "resource_code", resource,
                "quantity", quantity,
                "unit", unit,
                "period", eventTime.toLocalDate().toString().substring(0, 7),
                "cost", cost,
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "status", status), actor);

        if (accountId != null && cost.compareTo(BigDecimal.ZERO) > 0 &&
                !"false".equalsIgnoreCase(String.valueOf(request.getOrDefault("chargeBalance", true)))) {
            RecordTransactionCommand command = new RecordTransactionCommand();
            command.setAccountId(accountId);
            command.setType("CONSUME");
            command.setAmount(cost);
            command.setReferenceType("USAGE_EVENT");
            command.setReferenceId(eventId);
            command.setDescription(resource + " 用量计费 " + quantity + " " + unit);
            transactionService.recordTransaction(command, actor);
            store.update("usageRecord",
                    BillingValues.rowLong(store.findOne("usageRecord", Map.of("event_id", eventId)).orElseThrow(), "id"),
                    Map.of("status", "BILLED"), actor);
            store.update("usageEvent", eventPk, Map.of("status", "BILLED"), actor);
        }

        return usageEnvelope(store.findById("usageEvent", eventPk).orElseThrow());
    }

    public List<Map<String, Object>> usage(String tenantId, String resource, int page, int size) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (tenantId != null && !tenantId.isBlank()) {
            filters.put("tenant_id", tenantId);
        }
        if (resource != null && !resource.isBlank()) {
            filters.put("resource_code", resource.toUpperCase());
        }
        return store.list("usageRecord", filters, page, size);
    }

    public List<Map<String, Object>> summary(String tenantId, String period) {
        List<Map<String, Object>> records = store.list(
                "usageRecord", map("tenant_id", tenantId, "period", period), 1, 5000);
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> record : records) {
            String resource = BillingValues.rowString(record, "resource_code");
            Map<String, Object> item = grouped.computeIfAbsent(resource, key -> map(
                    "resource", key,
                    "quantity", BigDecimal.ZERO,
                    "cost", BigDecimal.ZERO,
                    "currency", BillingValues.rowString(record, "currency")));
            item.put("quantity", BillingValues.rowDecimal(item, "quantity")
                    .add(BillingValues.rowDecimal(record, "quantity")));
            item.put("cost", BillingValues.rowDecimal(item, "cost")
                    .add(BillingValues.rowDecimal(record, "cost")));
        }
        return new ArrayList<>(grouped.values());
    }

    @Transactional
    public List<Map<String, Object>> aggregate(String date, String actor) {
        LocalDate target = date == null || date.isBlank() ? LocalDate.now().minusDays(1) : LocalDate.parse(date);
        List<Map<String, Object>> records = store.list("usageRecord", Map.of(), 1, 5000).stream()
                .filter(row -> toDate(row.get("create_time")).equals(target))
                .toList();
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> record : records) {
            String key = BillingValues.rowString(record, "tenant_id") + "|" +
                    BillingValues.rowString(record, "resource_code") + "|" +
                    BillingValues.rowString(record, "currency");
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        groups.forEach((key, values) -> {
            Map<String, Object> first = values.get(0);
            String resource = BillingValues.rowString(first, "resource_code");
            String aggregation = store.findOne("meter", Map.of("resource_code", resource))
                    .map(row -> BillingValues.rowString(row, "aggregation_type"))
                    .orElse("SUM");
            BigDecimal quantity = aggregateQuantity(values, aggregation);
            BigDecimal cost = values.stream()
                    .map(row -> BillingValues.rowDecimal(row, "cost"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> filters = map(
                    "tenant_id", BillingValues.rowString(first, "tenant_id"),
                    "resource_code", resource,
                    "usage_date", target.toString());
            Map<String, Object> valuesToSave = map(
                    "tenant_id", BillingValues.rowString(first, "tenant_id"),
                    "resource_code", resource,
                    "usage_date", target.toString(),
                    "quantity", quantity,
                    "cost", cost,
                    "currency", BillingValues.rowString(first, "currency"));
            var existing = store.findOne("usageDaily", filters);
            if (existing.isPresent()) {
                store.update("usageDaily", BillingValues.rowLong(existing.get(), "id"), valuesToSave, actor);
                result.add(store.findById("usageDaily",
                        BillingValues.rowLong(existing.get(), "id")).orElseThrow());
            } else {
                long id = store.insert("usageDaily", valuesToSave, actor);
                result.add(store.findById("usageDaily", id).orElseThrow());
            }
        });
        return result;
    }

    @Scheduled(cron = "${core.billing.metering.aggregate-cron:0 10 0 * * *}")
    public void scheduledAggregate() {
        aggregate(LocalDate.now().minusDays(1).toString(), "metering-job");
    }

    private Map<String, Object> usageEnvelope(Map<String, Object> event) {
        Map<String, Object> result = new LinkedHashMap<>(event);
        store.findOne("usageRecord", Map.of("event_id", BillingValues.rowString(event, "event_id")))
                .ifPresent(record -> result.put("record", record));
        return result;
    }

    private BigDecimal aggregateQuantity(List<Map<String, Object>> values, String aggregation) {
        return switch (aggregation) {
            case "COUNT" -> BigDecimal.valueOf(values.size());
            case "MAX" -> values.stream()
                    .map(row -> BillingValues.rowDecimal(row, "quantity"))
                    .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            case "AVERAGE" -> values.stream()
                    .map(row -> BillingValues.rowDecimal(row, "quantity"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
            default -> values.stream()
                    .map(row -> BillingValues.rowDecimal(row, "quantity"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        };
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_USAGE_METADATA_INVALID", "metadata 无法序列化");
        }
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private LocalDateTime parseDateTime(Object value) {
        return BillingValues.dateTime(value);
    }

    private LocalDate toDate(Object value) {
        return parseDateTime(value).toLocalDate();
    }
}
