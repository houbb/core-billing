package io.coreplatform.billing.integration;

import io.coreplatform.billing.CoreBillingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = CoreBillingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("sqlite")
class BillingPlatformEndToEndTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void shouldCompleteP1ToP9CommercialLifecycle() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String tenant = "platform-" + suffix;

        // P0/P1: 账户、充值、冻结、确认扣款。
        Map<String, Object> account = post("/admin/accounts", Map.of(
                "name", "Platform " + suffix,
                "type", "ORGANIZATION"), tenant);
        Long accountId = number(account.get("id")).longValue();
        ResponseEntity<Map> forbiddenAccount = rest.exchange(
                baseUrl() + "/accounts/" + accountId,
                HttpMethod.GET,
                new HttpEntity<>(headers("other-" + tenant, "USER")),
                Map.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenAccount.getStatusCode());
        post("/accounts/" + accountId + "/deposit", Map.of(
                "amount", 100,
                "referenceId", "dep-" + suffix), tenant);
        Map<String, Object> frozen = post("/balance/freeze", Map.of(
                "accountId", accountId,
                "amount", 10,
                "referenceId", "freeze-" + suffix), tenant);
        String balanceReservation = String.valueOf(frozen.get("reservation_no"));
        post("/balance/consume", Map.of(
                "reservationNo", balanceReservation,
                "amount", 8), tenant);
        Map<String, Object> balance = get("/accounts/" + accountId + "/balance", tenant);
        assertMoney("92.00", balance.get("available"));
        assertMoney("0.00", balance.get("frozen"));

        // P2: 默认版本化价格。
        Map<String, Object> price = post("/pricing/calculate", Map.of(
                "resource", "AI_TOKEN",
                "quantity", 1000,
                "context", Map.of()), tenant);
        assertMoney("0.01", price.get("cost"));

        // P3: 幂等用量事件与计价记录。
        String eventId = "usage-" + suffix;
        Map<String, Object> usage = post("/usage/events", Map.of(
                "eventId", eventId,
                "accountId", accountId,
                "resource", "AI_TOKEN",
                "quantity", 1000,
                "unit", "TOKEN",
                "metadata", Map.of(),
                "chargeBalance", false), tenant);
        assertEquals(eventId, usage.get("event_id"));
        Map<String, Object> usageAgain = post("/usage/events", Map.of(
                "eventId", eventId,
                "resource", "AI_TOKEN",
                "quantity", 1000,
                "unit", "TOKEN"), tenant);
        assertEquals(number(usage.get("id")).longValue(), number(usageAgain.get("id")).longValue());

        // P4: 配额两阶段。
        post("/admin/quota/allocations", Map.of(
                "tenantId", tenant,
                "resourceCode", "AI_TOKEN",
                "quotaTotal", 10000,
                "policy", "BLOCK"), tenant);
        Map<String, Object> quotaReserved = post("/quota/reserve", Map.of(
                "resource", "AI_TOKEN",
                "amount", 1000,
                "referenceId", "quota-" + suffix), tenant);
        post("/quota/commit", Map.of(
                "reservationNo", quotaReserved.get("reservation_no"),
                "amount", 800), tenant);
        List<?> quotas = getList("/quota/" + tenant, tenant);
        assertMoney("800", ((Map<?, ?>) quotas.get(0)).get("used"));

        // P5: 默认 Pro 套餐、试用和额度联动。
        Map<String, Object> subscription = post("/subscriptions", Map.of("plan", "PRO"), tenant);
        assertEquals("TRIAL", subscription.get("status"));
        Map<String, Object> current = get("/subscription/current", tenant);
        assertEquals(number(subscription.get("id")).longValue(), number(current.get("id")).longValue());

        // P6: MOCK 支付回调联动充值，再执行全额退款。
        Map<String, Object> order = post("/payments/orders", Map.of(
                "businessType", "TOP_UP",
                "businessId", String.valueOf(accountId),
                "accountId", accountId,
                "amount", 50,
                "channelCode", "MOCK",
                "idempotencyKey", "pay-" + suffix), tenant);
        String orderNo = String.valueOf(order.get("order_no"));
        post("/admin/payments/orders/" + orderNo + "/mock-complete", Map.of(), tenant);
        assertMoney("142", get("/accounts/" + accountId + "/balance", tenant).get("available"));
        post("/payments/orders/" + orderNo + "/refund", Map.of("reason", "E2E refund"), tenant);
        assertMoney("92", get("/accounts/" + accountId + "/balance", tenant).get("available"));

        // P7: 自动汇总用量和订阅，生成账单与可下载 PDF。
        Map<String, Object> invoice = post("/admin/invoices/generate", Map.of(
                "tenantId", tenant,
                "period", YearMonth.now().toString(),
                "country", "CN"), tenant);
        assertTrue(number(invoice.get("total")).doubleValue() > 0);
        ResponseEntity<byte[]> pdf = exchangeBytes(
                "/invoices/" + invoice.get("id") + "/pdf", tenant);
        assertEquals(HttpStatus.OK, pdf.getStatusCode());
        assertNotNull(pdf.getBody());
        assertTrue(new String(pdf.getBody(), 0, 4).equals("%PDF"));
        ResponseEntity<Map> forbiddenInvoice = rest.exchange(
                baseUrl() + "/invoices/" + invoice.get("id"),
                HttpMethod.GET,
                new HttpEntity<>(headers("other-" + tenant, "USER")),
                Map.class);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenInvoice.getStatusCode());

        // 通过 Invoice 支付形成 Settlement 和可统计收入。
        ResponseEntity<Map> mismatchedPayment = rest.postForEntity(
                baseUrl() + "/payments/orders",
                new HttpEntity<>(Map.of(
                        "businessType", "INVOICE",
                        "businessId", String.valueOf(invoice.get("id")),
                        "amount", 999,
                        "channelCode", "MOCK",
                        "idempotencyKey", "bad-invoice-pay-" + suffix), headers(tenant)),
                Map.class);
        assertEquals(HttpStatus.CONFLICT, mismatchedPayment.getStatusCode());
        Map<String, Object> invoiceOrder = post("/payments/orders", Map.of(
                "businessType", "INVOICE",
                "businessId", String.valueOf(invoice.get("id")),
                "amount", invoice.get("total"),
                "channelCode", "MOCK",
                "idempotencyKey", "invoice-pay-" + suffix), tenant);
        post("/admin/payments/orders/" + invoiceOrder.get("order_no") + "/mock-complete",
                Map.of(), tenant);
        assertEquals("PAID", get("/invoices/" + invoice.get("id"), tenant).get("status"));

        // P8: Revenue / Profit / KPI / Forecast。
        post("/admin/finance/costs", Map.of(
                "tenantId", tenant,
                "resourceCode", "AI_TOKEN",
                "provider", "OpenAI",
                "cost", 10,
                "recordDate", LocalDate.now().toString()), tenant);
        Map<String, Object> finance = post(
                "/admin/finance/snapshots?date=" + LocalDate.now() + "&periodType=DAY",
                Map.of(), tenant);
        assertTrue(number(((Map<?, ?>) finance.get("revenue")).get("net_revenue")).doubleValue() > 0);
        assertNotNull(finance.get("forecast"));

        // P9: 汇率、预算、合同、审批、Marketplace 分账。
        post("/admin/enterprise/currency-rates", Map.of(
                "fromCurrency", "USD",
                "toCurrency", "CNY",
                "rate", 7.2), tenant);
        Map<String, Object> converted = post("/enterprise/currency/convert", Map.of(
                "fromCurrency", "USD",
                "toCurrency", "CNY",
                "amount", 10), tenant);
        assertMoney("72", converted.get("convertedAmount"));

        post("/admin/enterprise/budgets", Map.of(
                "scopeType", "DEPARTMENT",
                "scopeId", "RND",
                "period", YearMonth.now().toString(),
                "budgetAmount", 1000,
                "warningThreshold", 80,
                "policy", "BLOCK"), tenant);
        Map<String, Object> budget = post("/enterprise/budgets/check", Map.of(
                "scopeType", "DEPARTMENT",
                "scopeId", "RND",
                "period", YearMonth.now().toString(),
                "amount", 800), tenant);
        assertEquals(true, budget.get("allowed"));

        Map<String, Object> contract = post("/admin/enterprise/contracts", Map.of(
                "customer", "Enterprise " + suffix,
                "amount", 100000,
                "paymentTerm", "NET30"), tenant);
        Map<String, Object> approval = post("/admin/enterprise/approvals", Map.of(
                "businessType", "CONTRACT",
                "businessId", contract.get("contract_no"),
                "amount", 100000,
                "reason", "合同审批"), tenant);
        post("/admin/enterprise/approvals/" + approval.get("id") + "/approve",
                Map.of("comment", "approved"), tenant);
        Map<String, Object> executed = post(
                "/admin/enterprise/approvals/" + approval.get("id") + "/execute",
                Map.of(), tenant);
        assertEquals("EXECUTED", executed.get("status"));

        Map<String, Object> listing = post("/admin/enterprise/listings", Map.of(
                "listingCode", "PLUGIN_" + suffix,
                "creatorId", "creator-" + suffix,
                "listingName", "AI Plugin",
                "listingType", "PLUGIN",
                "price", 100,
                "platformRate", 0.2), tenant);
        Map<String, Object> marketplaceOrder = post("/admin/enterprise/marketplace-orders", Map.of(
                "listingId", listing.get("id"),
                "buyerId", "buyer-" + suffix), tenant);
        List<?> shares = (List<?>) marketplaceOrder.get("revenueShares");
        assertEquals(2, shares.size());

        Map<String, Object> enterpriseDashboard = get(
                "/admin/enterprise/dashboard?tenantId=" + tenant, tenant);
        assertTrue(number(enterpriseDashboard.get("contracts")).intValue() >= 1);
        assertTrue(number(enterpriseDashboard.get("budgetUsed")).doubleValue() >= 800);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body, String tenant) {
        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl() + path, new HttpEntity<>(body, headers(tenant)), Map.class);
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                () -> path + " returned " + response.getStatusCode() + " " + response.getBody());
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path, String tenant) {
        ResponseEntity<Map> response = rest.exchange(
                baseUrl() + path, HttpMethod.GET,
                new HttpEntity<>(headers(tenant)), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), path);
        return response.getBody();
    }

    private List<?> getList(String path, String tenant) {
        ResponseEntity<List> response = rest.exchange(
                baseUrl() + path, HttpMethod.GET,
                new HttpEntity<>(headers(tenant)), List.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), path);
        return response.getBody();
    }

    private ResponseEntity<byte[]> exchangeBytes(String path, String tenant) {
        return rest.exchange(baseUrl() + path, HttpMethod.GET,
                new HttpEntity<>(headers(tenant)), byte[].class);
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/billing";
    }

    private HttpHeaders headers(String tenant) {
        return headers(tenant, "SUPER_ADMIN");
    }

    private HttpHeaders headers(String tenant, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "e2e-user");
        headers.set("X-Tenant-Id", tenant);
        headers.set("X-Role", role);
        return headers;
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private void assertMoney(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(String.valueOf(actual))),
                () -> "expected " + expected + " but got " + actual);
    }
}
