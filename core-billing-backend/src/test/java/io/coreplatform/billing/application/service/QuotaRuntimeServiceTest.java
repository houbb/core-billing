package io.coreplatform.billing.application.service;

import io.coreplatform.billing.application.exception.BillingBusinessException;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaRuntimeServiceTest {

    @Mock
    private BillingRuntimeStore store;

    private QuotaRuntimeService service;

    @BeforeEach
    void setUp() {
        service = new QuotaRuntimeService(store);
    }

    @Test
    void shouldRejectReserveWhenBlockQuotaExceeded() {
        when(store.findOne("quotaReservation", Map.of("reference_id", "req-1")))
                .thenReturn(java.util.Optional.empty());
        when(store.list("quotaAllocation", Map.of(
                "tenant_id", "tenant",
                "resource_code", "AI_TOKEN",
                "status", "ACTIVE"), 1, 1000))
                .thenReturn(List.of(Map.of(
                        "id", 1L,
                        "tenant_id", "tenant",
                        "resource_code", "AI_TOKEN",
                        "quota_total", new BigDecimal("100"),
                        "quota_used", new BigDecimal("90"),
                        "quota_reserved", BigDecimal.ZERO,
                        "period_start", LocalDate.now().withDayOfMonth(1).toString(),
                        "period_end", LocalDate.now().withDayOfMonth(
                                LocalDate.now().lengthOfMonth()).toString(),
                        "policy", "BLOCK",
                        "status", "ACTIVE")));
        when(store.mutateQuota(1L, BigDecimal.ZERO, new BigDecimal("20"), true, "user"))
                .thenReturn(false);

        BillingBusinessException exception = assertThrows(
                BillingBusinessException.class,
                () -> service.reserve(Map.of(
                        "resource", "AI_TOKEN",
                        "amount", 20,
                        "referenceId", "req-1"), "tenant", "user"));

        assertEquals("BILLING_QUOTA_EXCEEDED", exception.getErrorCode());
    }
}

