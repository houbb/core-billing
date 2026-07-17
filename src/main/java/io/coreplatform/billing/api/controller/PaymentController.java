package io.coreplatform.billing.api.controller;

import io.coreplatform.billing.api.security.RequireRole;
import io.coreplatform.billing.api.security.SecurityContext;
import io.coreplatform.billing.application.service.PaymentRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
public class PaymentController {

    private final PaymentRuntimeService service;

    public PaymentController(PaymentRuntimeService service) {
        this.service = service;
    }

    @PostMapping("/payments/orders")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> request) {
        if (!SecurityContext.isSuperAdmin()) {
            request.put("tenantId", SecurityContext.getTenantId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOrder(
                request, SecurityContext.getTenantId(), SecurityContext.getUserId()));
    }

    @GetMapping("/payments/orders")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> orders() {
        return service.orders(SecurityContext.getTenantId());
    }

    @PostMapping("/payments/callback/{channelCode}")
    public Map<String, Object> callback(
            @PathVariable String channelCode,
            @RequestHeader(value = "X-Payment-Signature", required = false) String signature,
            @RequestBody Map<String, Object> request) {
        request.putIfAbsent("channelCode", channelCode);
        return service.callback(request, signature, "payment-callback");
    }

    @PostMapping("/payments/orders/{orderNo}/refund")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> refund(
            @PathVariable String orderNo, @RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.refund(orderNo, request, SecurityContext.getUserId()));
    }

    @GetMapping("/admin/payments/channels")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> channels() {
        return service.channels();
    }

    @PostMapping("/admin/payments/channels")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> createChannel(
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createChannel(request, SecurityContext.getUserId()));
    }

    @GetMapping("/admin/payments/orders")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> adminOrders(
            @RequestParam(required = false) String tenantId) {
        return service.orders(SecurityContext.isSuperAdmin()
                ? tenantId : SecurityContext.getTenantId());
    }

    @PostMapping("/admin/payments/orders/{orderNo}/mock-complete")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> mockComplete(@PathVariable String orderNo) {
        return service.simulateSuccess(orderNo, SecurityContext.getUserId());
    }

    @GetMapping("/admin/payments/callbacks")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> callbacks() {
        return service.callbacks();
    }

    @GetMapping("/admin/payments/refunds")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> refunds() {
        return service.refunds();
    }

    @PostMapping("/admin/payments/reconcile")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> reconcile(@RequestParam(required = false) String date) {
        return service.reconcile(date, SecurityContext.getUserId());
    }
}
