package io.coreplatform.billing.api.controller;

import io.coreplatform.billing.api.security.RequireRole;
import io.coreplatform.billing.api.security.SecurityContext;
import io.coreplatform.billing.application.service.InvoiceRuntimeService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
public class InvoiceController {

    private final InvoiceRuntimeService service;

    public InvoiceController(InvoiceRuntimeService service) {
        this.service = service;
    }

    @GetMapping("/invoices")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> invoices() {
        return service.invoices(SecurityContext.getTenantId());
    }

    @GetMapping("/invoices/{invoiceId}")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> invoice(@PathVariable Long invoiceId) {
        return service.invoiceForTenant(
                invoiceId, SecurityContext.getTenantId(), SecurityContext.isSuperAdmin());
    }

    @GetMapping("/invoices/{invoiceId}/pdf")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<byte[]> pdf(@PathVariable Long invoiceId) {
        return download(service.pdfForTenant(
                        invoiceId, SecurityContext.getTenantId(), SecurityContext.isSuperAdmin()),
                "invoice-" + invoiceId + ".pdf",
                MediaType.APPLICATION_PDF);
    }

    @GetMapping("/invoices/{invoiceId}/excel")
    @RequireRole({"USER", "ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<byte[]> excel(@PathVariable Long invoiceId) {
        return download(service.excelForTenant(
                        invoiceId, SecurityContext.getTenantId(), SecurityContext.isSuperAdmin()),
                "invoice-" + invoiceId + ".xls",
                MediaType.parseMediaType("application/vnd.ms-excel"));
    }

    @PostMapping("/admin/invoices/generate")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, Object> request) {
        scopeTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generate(
                request, SecurityContext.getTenantId(), SecurityContext.getUserId()));
    }

    @GetMapping("/admin/invoices")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> adminInvoices(
            @RequestParam(required = false) String tenantId) {
        return service.invoices(SecurityContext.isSuperAdmin()
                ? tenantId : SecurityContext.getTenantId());
    }

    @PostMapping("/admin/invoices/{invoiceId}/settle")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public Map<String, Object> settle(
            @PathVariable Long invoiceId, @RequestBody Map<String, Object> request) {
        return service.settle(invoiceId, request, SecurityContext.getUserId());
    }

    @PostMapping("/admin/invoices/{invoiceId}/credit-notes")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> credit(
            @PathVariable Long invoiceId, @RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.credit(invoiceId, request, SecurityContext.getUserId()));
    }

    @PostMapping("/admin/invoices/tax-rules")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> taxRule(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createTaxRule(request, SecurityContext.getUserId()));
    }

    @GetMapping("/admin/statements")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> statements(
            @RequestParam(required = false) String tenantId) {
        return service.statements(SecurityContext.isSuperAdmin()
                ? tenantId : SecurityContext.getTenantId());
    }

    @GetMapping("/admin/settlements")
    @RequireRole({"ADMIN", "SUPER_ADMIN"})
    public List<Map<String, Object>> settlements() {
        return service.settlements();
    }

    private ResponseEntity<byte[]> download(byte[] body, String filename, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private void scopeTenant(Map<String, Object> request) {
        if (!SecurityContext.isSuperAdmin()) {
            request.put("tenantId", SecurityContext.getTenantId());
        }
    }
}
