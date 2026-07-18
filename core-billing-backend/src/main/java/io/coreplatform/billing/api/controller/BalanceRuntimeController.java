package io.coreplatform.billing.api.controller;

import io.coreplatform.billing.api.security.RequireRole;
import io.coreplatform.billing.api.security.SecurityContext;
import io.coreplatform.billing.application.service.BalanceRuntimeService;
import io.coreplatform.billing.application.service.AccountService;
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
public class BalanceRuntimeController {

    private final BalanceRuntimeService service;
    private final AccountService accountService;

    public BalanceRuntimeController(BalanceRuntimeService service, AccountService accountService) {
        this.service = service;
        this.accountService = accountService;
    }

    @GetMapping("/accounts/{accountId}/balances")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> balances(@PathVariable Long accountId) {
        authorize(accountId);
        return service.balances(accountId, SecurityContext.getUserId());
    }

    @PostMapping("/accounts/{accountId}/deposit")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> deposit(
            @PathVariable Long accountId, @RequestBody Map<String, Object> request) {
        authorize(accountId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.deposit(accountId, request, SecurityContext.getUserId()));
    }

    @PostMapping("/balance/freeze")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> freeze(@RequestBody Map<String, Object> request) {
        authorize(io.coreplatform.billing.application.support.BillingValues.longValue(
                request.get("accountId")));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.freeze(request, SecurityContext.getUserId()));
    }

    @PostMapping("/balance/consume")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> consume(@RequestBody Map<String, Object> request) {
        authorizeReservation(request);
        return service.consume(request, SecurityContext.getUserId());
    }

    @PostMapping("/balance/release")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> release(@RequestBody Map<String, Object> request) {
        authorizeReservation(request);
        return service.release(request, SecurityContext.getUserId());
    }

    @GetMapping("/balance/reservations/{reservationNo}")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> reservation(@PathVariable String reservationNo) {
        Map<String, Object> reservation = service.reservation(reservationNo);
        authorize(io.coreplatform.billing.application.support.BillingValues.rowLong(
                reservation, "account_id"));
        return reservation;
    }

    private void authorizeReservation(Map<String, Object> request) {
        String reservationNo = io.coreplatform.billing.application.support.BillingValues
                .requiredString(request, "reservationNo");
        Map<String, Object> reservation = service.reservation(reservationNo);
        authorize(io.coreplatform.billing.application.support.BillingValues.rowLong(
                reservation, "account_id"));
    }

    private void authorize(Long accountId) {
        accountService.getAuthorizedAccount(
                accountId, SecurityContext.getTenantId(), SecurityContext.isSuperAdmin());
    }
}
