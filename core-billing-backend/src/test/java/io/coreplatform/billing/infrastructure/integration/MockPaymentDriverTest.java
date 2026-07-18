package io.coreplatform.billing.infrastructure.integration;

import io.coreplatform.billing.infrastructure.config.BillingProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPaymentDriverTest {

    @Test
    void shouldSignAndVerifyCallback() {
        BillingProperties properties = new BillingProperties();
        properties.setPaymentCallbackSecret("test-secret");
        MockPaymentDriver driver = new MockPaymentDriver(properties);
        String payload = "cb|order|SUCCESS|100";

        String signature = driver.sign(payload);

        assertTrue(driver.verify(payload, signature));
        assertFalse(driver.verify(payload + "-tampered", signature));
    }
}

