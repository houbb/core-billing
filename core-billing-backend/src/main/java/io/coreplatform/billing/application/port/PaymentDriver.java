package io.coreplatform.billing.application.port;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentDriver {

    String code();

    Map<String, Object> create(String orderNo, BigDecimal amount, String currency);

    Map<String, Object> refund(String orderNo, String refundNo, BigDecimal amount);

    boolean verify(String payload, String signature);

    String sign(String payload);
}

