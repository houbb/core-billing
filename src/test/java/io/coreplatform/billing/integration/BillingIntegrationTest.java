package io.coreplatform.billing.integration;

import io.coreplatform.billing.CoreBillingApplication;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    classes = CoreBillingApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("sqlite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private static Long accountId;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/billing";
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-User-Id", "test-user");
        h.set("X-Tenant-Id", "test-tenant");
        h.set("X-Role", "SUPER_ADMIN");
        return h;
    }

    @Test
    @Order(1)
    void step1_createAccount() {
        Map<String, String> body = Map.of("name", "Echo测试账户", "type", "PERSONAL");
        var resp = restTemplate.postForEntity(
                baseUrl() + "/admin/accounts",
                new HttpEntity<>(body, headers()),
                Map.class);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertNotNull(resp.getBody());
        accountId = ((Number) resp.getBody().get("id")).longValue();
        assertTrue(accountId > 0);
    }

    @Test
    @Order(2)
    void step2_topUp() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", accountId);
        body.put("type", "TOP_UP");
        body.put("amount", 100.00);
        body.put("referenceType", "MANUAL");
        body.put("referenceId", "INTEG_TEST_TOPUP_001");

        var resp = restTemplate.postForEntity(
                baseUrl() + "/transactions",
                new HttpEntity<>(body, headers()),
                Map.class);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertNotNull(resp.getBody().get("transactionNo"));
    }

    @Test
    @Order(3)
    void step3_checkBalance() {
        var resp = restTemplate.getForEntity(
                baseUrl() + "/accounts/" + accountId + "/balance",
                Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(0, new BigDecimal("100.00").compareTo(toBigDecimal(resp.getBody().get("balance"))));
    }

    @Test
    @Order(4)
    void step4_consume() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", accountId);
        body.put("type", "CONSUME");
        body.put("amount", 5.00);
        body.put("referenceType", "AI_REQUEST");
        body.put("referenceId", "INTEG_TEST_CONSUME_001");
        body.put("description", "GPT-5 API 调用");

        var resp = restTemplate.postForEntity(
                baseUrl() + "/transactions",
                new HttpEntity<>(body, headers()),
                Map.class);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    @Order(5)
    void step5_checkBalanceAfterConsume() {
        var resp = restTemplate.getForEntity(
                baseUrl() + "/accounts/" + accountId + "/balance",
                Map.class);

        assertEquals(0, new BigDecimal("95.00").compareTo(toBigDecimal(resp.getBody().get("balance"))));
    }

    @Test
    @Order(6)
    void step6_refund() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", accountId);
        body.put("type", "REFUND");
        body.put("amount", 5.00);
        body.put("referenceType", "MANUAL");
        body.put("referenceId", "INTEG_TEST_REFUND_001");
        body.put("description", "服务异常退款");

        var resp = restTemplate.postForEntity(
                baseUrl() + "/transactions",
                new HttpEntity<>(body, headers()),
                Map.class);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    @Order(7)
    void step7_checkBalanceAfterRefund() {
        var resp = restTemplate.getForEntity(
                baseUrl() + "/accounts/" + accountId + "/balance",
                Map.class);

        assertEquals(0, new BigDecimal("100.00").compareTo(toBigDecimal(resp.getBody().get("balance"))));
    }

    @Test
    @Order(8)
    void step8_idempotentTest() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", accountId);
        body.put("type", "CONSUME");
        body.put("amount", 5.00);
        body.put("referenceType", "AI_REQUEST");
        body.put("referenceId", "INTEG_TEST_CONSUME_001");

        var resp = restTemplate.postForEntity(
                baseUrl() + "/transactions",
                new HttpEntity<>(body, headers()),
                Map.class);

        // Power returns existing record
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    @Order(9)
    void step9_adjustBalance() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", 50.00);
        body.put("reason", "集成测试补偿");

        var resp = restTemplate.postForEntity(
                baseUrl() + "/admin/accounts/" + accountId + "/adjust",
                new HttpEntity<>(body, headers()),
                Map.class);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    @Order(10)
    void step10_finalBalance() {
        var resp = restTemplate.getForEntity(
                baseUrl() + "/accounts/" + accountId + "/balance",
                Map.class);

        // 100 + 50 = 150
        assertEquals(0, new BigDecimal("150.00").compareTo(toBigDecimal(resp.getBody().get("balance"))));
    }

    @Test
    @Order(11)
    void step11_transactionList() {
        var resp = restTemplate.exchange(
                baseUrl() + "/transactions/account/" + accountId + "?page=1&size=50",
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        List<?> items = (List<?>) resp.getBody().get("items");
        assertNotNull(items);
        assertTrue(items.size() >= 4, "Expected at least 4 transactions, got " + items.size());
    }

    @Test
    @Order(12)
    void step12_adminAccounts() {
        var resp = restTemplate.exchange(
                baseUrl() + "/admin/accounts?page=1&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody().get("items"));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd.stripTrailingZeros();
        if (value instanceof Number n) return new BigDecimal(n.toString()).stripTrailingZeros();
        return new BigDecimal(String.valueOf(value)).stripTrailingZeros();
    }
}