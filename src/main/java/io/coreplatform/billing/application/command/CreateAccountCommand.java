package io.coreplatform.billing.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateAccountCommand {

    @NotBlank(message = "账户名称不能为空")
    private String name;

    @NotBlank(message = "账户类型不能为空")
    private String type;  // PERSONAL / ORGANIZATION

    private String tenantId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}