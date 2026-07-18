package io.coreplatform.billing.api.controller;

import io.coreplatform.billing.api.security.RequireRole;
import io.coreplatform.billing.api.security.SecurityContext;
import io.coreplatform.billing.application.service.FinanceRuntimeService;
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
@RequestMapping("/api/v1/billing/admin/finance")
@RequireRole({"ADMIN", "SUPER_ADMIN"})
public class FinanceController {

    private final FinanceRuntimeService service;

    public FinanceController(FinanceRuntimeService service) {
        this.service = service;
    }

    @PostMapping("/costs")
    public ResponseEntity<Map<String, Object>> addCost(@RequestBody Map<String, Object> request) {
        if (!SecurityContext.isSuperAdmin()) {
            request.put("tenantId", SecurityContext.getTenantId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addCost(
                request, SecurityContext.getTenantId(), SecurityContext.getUserId()));
    }

    @PostMapping("/snapshots")
    public Map<String, Object> snapshot(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "DAY") String periodType) {
        return service.snapshot(date, periodType, SecurityContext.getUserId());
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return service.dashboard();
    }

    @GetMapping("/customers")
    public List<Map<String, Object>> customers() {
        return service.customers();
    }

    @GetMapping("/products")
    public List<Map<String, Object>> products() {
        return service.products();
    }

    @GetMapping("/forecasts")
    public List<Map<String, Object>> forecasts() {
        return service.forecasts();
    }
}
