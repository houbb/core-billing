package io.coreplatform.billing.infrastructure.integration;

import io.coreplatform.billing.application.port.PaymentDriver;
import io.coreplatform.billing.application.support.BillingValues;
import io.coreplatform.billing.infrastructure.config.BillingProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Component
public class MockPaymentDriver implements PaymentDriver {

    private final BillingProperties properties;

    public MockPaymentDriver(BillingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return "MOCK";
    }

    @Override
    public Map<String, Object> create(String orderNo, BigDecimal amount, String currency) {
        return Map.of(
                "providerTradeNo", BillingValues.number("MOCKPAY"),
                "paymentUrl", "/mock-pay/" + orderNo,
                "status", "PENDING");
    }

    @Override
    public Map<String, Object> refund(String orderNo, String refundNo, BigDecimal amount) {
        return Map.of(
                "providerRefundNo", BillingValues.number("MOCKREF"),
                "status", "SUCCESS");
    }

    @Override
    public boolean verify(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(payload).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getPaymentCallbackSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成支付回调签名", exception);
        }
    }
}

