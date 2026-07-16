package io.coreplatform.billing.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class AdjustBalanceCommand {

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;  // 正数为增加，负数为扣减

    @NotBlank(message = "调整原因不能为空")
    private String reason;

    private String description;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}