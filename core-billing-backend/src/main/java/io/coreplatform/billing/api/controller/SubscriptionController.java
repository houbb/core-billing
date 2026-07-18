package io.coreplatform.billing.api.controller;

import io.coreplatform.billing.api.security.RequireRole;
import io.coreplatform.billing.api.security.SecurityContext;
import io.coreplatform.billing.application.service.SubscriptionRuntimeService;
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
public class SubscriptionController {

    private final SubscriptionRuntimeService service;

    public SubscriptionController(SubscriptionRuntimeService service) {
        this.service = service;
    }

    @GetMapping("/plans")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> plans() {
        return service.plans(false);
    }

    @PostMapping("/subscriptions")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> subscribe(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.subscribe(
                request, SecurityContext.getTenantId(), SecurityContext.getUserId()));
    }

    @GetMapping("/subscription/current")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> current() {
        return service.current(SecurityContext.getTenantId());
    }

    @PostMapping("/subscription/change-plan")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> changePlan(@RequestBody Map<String, Object> request) {
        return service.changePlan(
                request, SecurityContext.getTenantId(), SecurityContext.getUserId());
    }

    @PostMapping("/subscription/{action}")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> lifecycle(@PathVariable String action) {
        return service.lifecycle(
                action, SecurityContext.getTenantId(), SecurityContext.getUserId());
    }

    @GetMapping("/admin/subscription/products")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> products() {
        return service.products();
    }

    @PostMapping("/admin/subscription/products")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> createProduct(
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createProduct(request, SecurityContext.getUserId()));
    }

    @GetMapping("/admin/subscription/plans")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> adminPlans() {
        return service.plans(true);
    }

    @PostMapping("/admin/subscription/plans")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> createPlan(
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createPlan(request, SecurityContext.getUserId()));
    }

    @PostMapping("/admin/subscription/plans/{planId}/versions")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> publishVersion(
            @PathVariable Long planId, @RequestBody Map<String, Object> request) {
        return service.publishVersion(planId, request, SecurityContext.getUserId());
    }

    @GetMapping("/admin/subscriptions")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> subscriptions(
            @RequestParam(required = false) String tenantId) {
        return service.subscriptions(SecurityContext.isSuperAdmin()
                ? tenantId : SecurityContext.getTenantId());
    }
}
