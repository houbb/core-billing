package io.coreplatform.billing.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingRuntimeServiceTest {

    @Mock
    private BillingRuntimeStore store;

    private PricingRuntimeService service;

    @BeforeEach
    void setUp() {
        service = new PricingRuntimeService(store, new ObjectMapper());
    }

    @Test
    void shouldCalculateUnitPrice() {
        Map<String, Object> rule = Map.of(
                "id", 1L,
                "resource_code", "AI_TOKEN",
                "rule_name", "GPT5",
                "pricing_mode", "UNIT",
                "unit_quantity", new BigDecimal("1000"),
                "condition_json", "{}",
                "tier_json", "[]",
                "status", "ACTIVE");
        Map<String, Object> version = Map.of(
                "id", 1L,
                "rule_id", 1L,
                "version_no", 1,
                "price", new BigDecimal("0.01"),
                "effective_time", Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
                "status", "ACTIVE");
        when(store.list("priceRule",
                Map.of("resource_code", "AI_TOKEN", "status", "ACTIVE"), 1, 1000))
                .thenReturn(List.of(rule));
        when(store.list("priceVersion", Map.of("rule_id", 1L), 1, 1000))
                .thenReturn(List.of(version));

        Map<String, Object> result = service.calculate(Map.of(
                "resource", "AI_TOKEN",
                "quantity", 12_000,
                "context", Map.of()));

        assertEquals(0, new BigDecimal("0.12")
                .compareTo((BigDecimal) result.get("cost")));
        assertEquals("GPT5", result.get("rule"));
    }

    @Test
    void shouldCalculateProgressiveTiers() {
        Map<String, Object> rule = Map.of(
                "id", 2L,
                "resource_code", "STORAGE_GB",
                "rule_name", "Storage tier",
                "pricing_mode", "TIERED",
                "unit_quantity", BigDecimal.ONE,
                "condition_json", "{}",
                "tier_json", """
                        [
                          {"upTo":100,"price":0.10},
                          {"upTo":1000,"price":0.08},
                          {"price":0.05}
                        ]
                        """,
                "status", "ACTIVE");
        Map<String, Object> version = Map.of(
                "id", 2L,
                "rule_id", 2L,
                "version_no", 1,
                "price", new BigDecimal("0.05"),
                "effective_time", Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
                "status", "ACTIVE");
        when(store.list("priceRule",
                Map.of("resource_code", "STORAGE_GB", "status", "ACTIVE"), 1, 1000))
                .thenReturn(List.of(rule));
        when(store.list("priceVersion", Map.of("rule_id", 2L), 1, 1000))
                .thenReturn(List.of(version));

        Map<String, Object> result = service.calculate(Map.of(
                "resource", "STORAGE_GB",
                "quantity", 1100,
                "context", Map.of()));

        assertEquals(0, new BigDecimal("87.00")
                .compareTo((BigDecimal) result.get("cost")));
    }
}

