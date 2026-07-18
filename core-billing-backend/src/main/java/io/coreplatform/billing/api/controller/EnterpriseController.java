package io.coreplatform.billing.api.controller;

import io.coreplatform.billing.api.security.RequireRole;
import io.coreplatform.billing.api.security.SecurityContext;
import io.coreplatform.billing.application.service.EnterpriseRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
public class EnterpriseController {

    private final EnterpriseRuntimeService service;

    public EnterpriseController(EnterpriseRuntimeService service) {
        this.service = service;
    }

    @GetMapping("/admin/enterprise/{module}")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> list(
            @PathVariable String module,
            @RequestParam(required = false) String tenantId) {
        return service.list(module, SecurityContext.isSuperAdmin()
                ? tenantId : SecurityContext.getTenantId());
    }

    @PostMapping("/admin/enterprise/{module}")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String module, @RequestBody Map<String, Object> request) {
        if (!SecurityContext.isSuperAdmin()) {
            request.put("tenantId", SecurityContext.getTenantId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(
                module, request, SecurityContext.getTenantId(), SecurityContext.getUserId()));
    }

    @PostMapping("/admin/enterprise/contracts/{contractId}/{action}")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> contractAction(
            @PathVariable Long contractId, @PathVariable String action) {
        return service.contractAction(
                contractId, action, SecurityContext.getTenantId(),
                SecurityContext.isSuperAdmin(), SecurityContext.getUserId());
    }

    @PostMapping("/enterprise/currency/convert")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> convert(@RequestBody Map<String, Object> request) {
        return service.convert(request);
    }

    @PostMapping("/enterprise/coupons/redeem")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> redeemCoupon(@RequestBody Map<String, Object> request) {
        return service.redeemCoupon(request, SecurityContext.getUserId());
    }

    @PostMapping("/enterprise/budgets/check")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> budgetCheck(@RequestBody Map<String, Object> request) {
        return service.budgetCheck(
                request, SecurityContext.getTenantId(), SecurityContext.getUserId());
    }

    @PostMapping("/admin/enterprise/approvals/{approvalId}/{action}")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> approvalAction(
            @PathVariable Long approvalId,
            @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> request) {
        return service.approvalAction(
                approvalId, action, request == null ? Map.of() : request,
                SecurityContext.getTenantId(), SecurityContext.isSuperAdmin(),
                SecurityContext.getUserId());
    }

    @PostMapping("/admin/enterprise/payout")
    @RequireRole({"SUPER_ADMIN"})
    public Map<String, Object> payout(@RequestBody Map<String, Object> request) {
        return service.payoutBeneficiary(request, SecurityContext.getUserId());
    }

    @GetMapping("/admin/enterprise/dashboard")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> dashboard(
            @RequestParam(required = false) String tenantId) {
        return service.dashboard(SecurityContext.isSuperAdmin()
                ? tenantId : SecurityContext.getTenantId());
    }
}
