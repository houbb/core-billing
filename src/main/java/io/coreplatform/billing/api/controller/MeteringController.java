package io.coreplatform.billing.api.controller;

import io.coreplatform.billing.api.security.RequireRole;
import io.coreplatform.billing.api.security.SecurityContext;
import io.coreplatform.billing.application.service.MeteringRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
public class MeteringController {

    private final MeteringRuntimeService service;

    public MeteringController(MeteringRuntimeService service) {
        this.service = service;
    }

    @PostMapping("/usage/events")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody Map<String, Object> request) {
        if (!SecurityContext.isSuperAdmin()) {
            request.put("tenantId", SecurityContext.getTenantId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ingest(
                request, SecurityContext.getTenantId(), SecurityContext.getUserId()));
    }

    @GetMapping("/usage")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> usage(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String resource,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        String scopedTenant = SecurityContext.isSuperAdmin()
                ? (tenantId == null ? SecurityContext.getTenantId() : tenantId)
                : SecurityContext.getTenantId();
        return service.usage(scopedTenant, resource, page, size);
    }

    @GetMapping("/usage/summary")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> summary(
            @RequestParam(required = false) String tenantId,
            @RequestParam String period) {
        String scopedTenant = SecurityContext.isSuperAdmin()
                ? (tenantId == null ? SecurityContext.getTenantId() : tenantId)
                : SecurityContext.getTenantId();
        return service.summary(scopedTenant, period);
    }

    @GetMapping("/admin/metering/meters")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> meters() {
        return service.meters();
    }

    @PostMapping("/admin/metering/meters")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> createMeter(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createMeter(request, SecurityContext.getUserId()));
    }

    @PostMapping("/admin/metering/aggregate")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> aggregate(@RequestParam(required = false) String date) {
        return service.aggregate(date, SecurityContext.getUserId());
    }
}
