package io.coreplatform.billing.api.controller;

import io.coreplatform.billing.api.security.RequireRole;
import io.coreplatform.billing.api.security.SecurityContext;
import io.coreplatform.billing.application.service.PricingRuntimeService;
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
public class PricingController {

    private final PricingRuntimeService service;

    public PricingController(PricingRuntimeService service) {
        this.service = service;
    }

    @GetMapping("/pricing/{resourceCode}")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> price(@PathVariable String resourceCode) {
        return service.quote(resourceCode, Map.of());
    }

    @PostMapping("/pricing/calculate")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> calculate(@RequestBody Map<String, Object> request) {
        return service.calculate(request);
    }

    @GetMapping("/admin/pricing/resources")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> resources() {
        return service.resources();
    }

    @PostMapping("/admin/pricing/resources")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> createResource(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createResource(request, SecurityContext.getUserId()));
    }

    @GetMapping("/admin/pricing/rules")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> rules(
            @RequestParam(required = false) String resourceCode) {
        return service.rules(resourceCode);
    }

    @PostMapping("/admin/pricing/rules")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> createRule(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createRule(request, SecurityContext.getUserId()));
    }

    @GetMapping("/admin/pricing/rules/{ruleId}")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> rule(@PathVariable Long ruleId) {
        return service.rule(ruleId);
    }

    @PostMapping("/admin/pricing/rules/{ruleId}/versions")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> createVersion(
            @PathVariable Long ruleId, @RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createVersion(ruleId, request, SecurityContext.getUserId()));
    }
}

