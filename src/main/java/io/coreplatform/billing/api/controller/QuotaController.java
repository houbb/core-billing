package io.coreplatform.billing.api.controller;

import io.coreplatform.billing.api.security.RequireRole;
import io.coreplatform.billing.api.security.SecurityContext;
import io.coreplatform.billing.application.service.QuotaRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
public class QuotaController {

    private final QuotaRuntimeService service;

    public QuotaController(QuotaRuntimeService service) {
        this.service = service;
    }

    @GetMapping("/quota/{tenantId}")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> check(@PathVariable String tenantId) {
        String scopedTenant = SecurityContext.isSuperAdmin()
                ? tenantId : SecurityContext.getTenantId();
        return service.check(scopedTenant);
    }

    @PostMapping("/quota/reserve")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> reserve(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reserve(
                request, SecurityContext.getTenantId(), SecurityContext.getUserId()));
    }

    @PostMapping("/quota/commit")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> commit(@RequestBody Map<String, Object> request) {
        authorizeReservation(request);
        return service.commit(request, SecurityContext.getUserId());
    }

    @PostMapping("/quota/release")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> release(@RequestBody Map<String, Object> request) {
        authorizeReservation(request);
        return service.release(request, SecurityContext.getUserId());
    }

    @PostMapping("/quota/consume")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> consume(@RequestBody Map<String, Object> request) {
        return service.consume(
                request, SecurityContext.getTenantId(), SecurityContext.getUserId());
    }

    @GetMapping("/quota/{tenantId}/alerts")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> alerts(@PathVariable String tenantId) {
        String scopedTenant = SecurityContext.isSuperAdmin()
                ? tenantId : SecurityContext.getTenantId();
        return service.alerts(scopedTenant);
    }

    @GetMapping("/admin/quota/definitions")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> definitions() {
        return service.definitions();
    }

    @PostMapping("/admin/quota/definitions")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> createDefinition(
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createDefinition(request, SecurityContext.getUserId()));
    }

    @PostMapping("/admin/quota/allocations")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> allocate(
            @RequestBody Map<String, Object> request) {
        scopeTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.allocate(request, SecurityContext.getUserId()));
    }

    private void authorizeReservation(Map<String, Object> request) {
        String reservationNo = io.coreplatform.billing.application.support.BillingValues
                .requiredString(request, "reservationNo");
        service.authorizeReservation(
                reservationNo, SecurityContext.getTenantId(), SecurityContext.isSuperAdmin());
    }

    private void scopeTenant(Map<String, Object> request) {
        if (!SecurityContext.isSuperAdmin()) {
            request.put("tenantId", SecurityContext.getTenantId());
        }
    }
}
