package io.coreplatform.billing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "core.billing")
public class BillingProperties {

    /**
     * 交易流水号前缀
     */
    private String transactionNoPrefix = "TX";

    /**
     * 默认币种
     */
    private String defaultCurrency = "CNY";

    public String getTransactionNoPrefix() { return transactionNoPrefix; }
    public void setTransactionNoPrefix(String transactionNoPrefix) { this.transactionNoPrefix = transactionNoPrefix; }

    public String getDefaultCurrency() { return defaultCurrency; }
    public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }
}