package io.coreplatform.billing.application.service;

import io.coreplatform.billing.application.domain.Account;
import io.coreplatform.billing.application.domain.Direction;
import io.coreplatform.billing.application.domain.Transaction;
import io.coreplatform.billing.application.exception.BillingBusinessException;
import io.coreplatform.billing.application.port.AccountRepository;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import io.coreplatform.billing.application.port.TransactionRepository;
import io.coreplatform.billing.application.support.BillingValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.coreplatform.billing.application.support.BillingValues.map;

@Service
public class InvoiceRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceRuntimeService.class);

    private final BillingRuntimeStore store;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public InvoiceRuntimeService(BillingRuntimeStore store,
                                 AccountRepository accountRepository,
                                 TransactionRepository transactionRepository) {
        this.store = store;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Map<String, Object> generate(Map<String, Object> request,
                                        String defaultTenant,
                                        String actor) {
        String tenantId = BillingValues.string(request, "tenantId", defaultTenant);
        String period = BillingValues.string(request, "period", BillingValues.month());
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(period);
        } catch (Exception exception) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_PERIOD_INVALID", "period 必须为 yyyy-MM");
        }
        var existing = store.findOne("invoice", map(
                "tenant_id", tenantId,
                "billing_period", period));
        if (existing.isPresent()) {
            return invoice(BillingValues.rowLong(existing.get(), "id"));
        }

        List<Map<String, Object>> items = buildItems(tenantId, period);
        BigDecimal subtotal = items.stream()
                .map(item -> BillingValues.decimal(item.get("amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = BillingValues.decimal(request.get("discount"));
        if (discount.compareTo(BigDecimal.ZERO) < 0 || discount.compareTo(subtotal) > 0) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_INVOICE_DISCOUNT_INVALID", "账单折扣金额无效");
        }
        BigDecimal taxable = subtotal.subtract(discount);
        BigDecimal taxRate = request.get("taxRate") == null
                ? defaultTaxRate(BillingValues.string(request, "country", "CN"))
                : BillingValues.decimal(request.get("taxRate"));
        BigDecimal tax = taxable.multiply(taxRate).setScale(6, RoundingMode.HALF_UP);
        BigDecimal total = taxable.add(tax);
        LocalDateTime generated = LocalDateTime.now();
        long invoiceId = store.insert("invoice", map(
                "invoice_no", BillingValues.number("INV"),
                "tenant_id", tenantId,
                "billing_period", period,
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "subtotal", subtotal,
                "tax", tax,
                "discount", discount,
                "total", total,
                "status", "GENERATED",
                "due_time", Timestamp.valueOf(generated.plusDays(
                        BillingValues.intValue(request.get("dueDays"), 30))),
                "generated_time", Timestamp.valueOf(generated)), actor);
        for (Map<String, Object> item : items) {
            Map<String, Object> persisted = new LinkedHashMap<>(item);
            persisted.put("invoice_id", invoiceId);
            store.insert("invoiceItem", persisted, actor);
        }
        generateStatement(tenantId, yearMonth, actor);
        return invoice(invoiceId);
    }

    public List<Map<String, Object>> invoices(String tenantId) {
        return store.list("invoice",
                tenantId == null || tenantId.isBlank()
                        ? Map.of() : Map.of("tenant_id", tenantId), 1, 5000);
    }

    public Map<String, Object> invoice(Long invoiceId) {
        Map<String, Object> invoice = store.findById("invoice", invoiceId)
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_INVOICE_NOT_FOUND", "账单不存在: " + invoiceId));
        invoice.put("items", store.list(
                "invoiceItem", Map.of("invoice_id", invoiceId), 1, 5000));
        invoice.put("creditNotes", store.list(
                "creditNote", Map.of("invoice_id", invoiceId), 1, 5000));
        invoice.put("settlements", store.list(
                "settlement", Map.of("invoice_id", invoiceId), 1, 5000));
        return invoice;
    }

    public Map<String, Object> invoiceForTenant(Long invoiceId, String tenantId, boolean superAdmin) {
        Map<String, Object> invoice = invoice(invoiceId);
        if (!superAdmin && !tenantId.equals(BillingValues.rowString(invoice, "tenant_id"))) {
            throw BillingBusinessException.forbidden(
                    "BILLING_INVOICE_TENANT_FORBIDDEN", "无权访问其他租户账单");
        }
        return invoice;
    }

    @Transactional
    public Map<String, Object> settle(Long invoiceId, Map<String, Object> request, String actor) {
        Map<String, Object> invoice = invoice(invoiceId);
        if ("PAID".equals(BillingValues.rowString(invoice, "status"))) {
            List<Map<String, Object>> settlements = store.list(
                    "settlement", Map.of("invoice_id", invoiceId), 1, 100);
            return settlements.isEmpty() ? invoice : settlements.get(0);
        }
        Long paymentOrderId = BillingValues.longValue(request.get("paymentOrderId"));
        if (paymentOrderId != null) {
            Map<String, Object> order = store.findById("paymentOrder", paymentOrderId)
                    .orElseThrow(() -> BillingBusinessException.notFound(
                            "BILLING_PAYMENT_ORDER_NOT_FOUND", "支付订单不存在"));
            if (!"SUCCESS".equals(BillingValues.rowString(order, "status"))) {
                throw BillingBusinessException.conflict(
                        "BILLING_SETTLEMENT_PAYMENT_PENDING", "支付订单尚未成功");
            }
        }
        long id = store.insert("settlement", map(
                "settlement_no", BillingValues.number("ST"),
                "invoice_id", invoiceId,
                "payment_order_id", paymentOrderId,
                "amount", BillingValues.rowDecimal(invoice, "total"),
                "status", "SUCCESS",
                "settled_time", Timestamp.valueOf(LocalDateTime.now())), actor);
        store.update("invoice", invoiceId, Map.of("status", "PAID"), actor);
        return store.findById("settlement", id).orElseThrow();
    }

    @Transactional
    public Map<String, Object> credit(Long invoiceId,
                                      Map<String, Object> request,
                                      String actor) {
        Map<String, Object> invoice = invoice(invoiceId);
        BigDecimal amount = BillingValues.positive(request, "amount");
        BigDecimal issued = store.sum("creditNote", "amount", Map.of(
                "invoice_id", invoiceId,
                "status", "ISSUED"));
        if (issued.add(amount).compareTo(BillingValues.rowDecimal(invoice, "total")) > 0) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_CREDIT_EXCEEDS_INVOICE", "贷项金额超过账单总额");
        }
        long id = store.insert("creditNote", map(
                "credit_no", BillingValues.number("CN"),
                "invoice_id", invoiceId,
                "amount", amount,
                "reason", BillingValues.requiredString(request, "reason"),
                "status", "ISSUED"), actor);
        return store.findById("creditNote", id).orElseThrow();
    }

    @Transactional
    public Map<String, Object> createTaxRule(Map<String, Object> request, String actor) {
        String country = BillingValues.requiredString(request, "country").toUpperCase();
        String type = BillingValues.requiredString(request, "taxType").toUpperCase();
        BigDecimal rate = BillingValues.decimal(request.get("rate"));
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_TAX_RATE_INVALID", "税率必须在 0 到 1 之间");
        }
        var existing = store.findOne("taxRule", map("country", country, "tax_type", type));
        if (existing.isPresent()) {
            store.update("taxRule", BillingValues.rowLong(existing.get(), "id"), map(
                    "rate", rate,
                    "status", "ACTIVE"), actor);
            return store.findById("taxRule", BillingValues.rowLong(existing.get(), "id")).orElseThrow();
        }
        long id = store.insert("taxRule", map(
                "country", country,
                "tax_type", type,
                "rate", rate,
                "status", "ACTIVE"), actor);
        return store.findById("taxRule", id).orElseThrow();
    }

    public List<Map<String, Object>> statements(String tenantId) {
        return store.list("statement",
                tenantId == null || tenantId.isBlank()
                        ? Map.of() : Map.of("tenant_id", tenantId), 1, 5000);
    }

    public List<Map<String, Object>> settlements() {
        return store.list("settlement", Map.of(), 1, 5000);
    }

    public byte[] pdf(Long invoiceId) {
        Map<String, Object> invoice = invoice(invoiceId);
        List<String> lines = new ArrayList<>();
        lines.add("CORE BILLING INVOICE");
        lines.add("Invoice No: " + BillingValues.rowString(invoice, "invoice_no"));
        lines.add("Tenant: " + ascii(BillingValues.rowString(invoice, "tenant_id")));
        lines.add("Period: " + BillingValues.rowString(invoice, "billing_period"));
        lines.add("Currency: " + BillingValues.rowString(invoice, "currency"));
        lines.add("Subtotal: " + BillingValues.rowDecimal(invoice, "subtotal").toPlainString());
        lines.add("Tax: " + BillingValues.rowDecimal(invoice, "tax").toPlainString());
        lines.add("Discount: " + BillingValues.rowDecimal(invoice, "discount").toPlainString());
        lines.add("Total: " + BillingValues.rowDecimal(invoice, "total").toPlainString());
        for (Object itemValue : (List<?>) invoice.get("items")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) itemValue;
            lines.add(ascii(BillingValues.rowString(item, "resource_code")) + " " +
                    BillingValues.rowDecimal(item, "quantity").toPlainString() + " " +
                    BillingValues.rowDecimal(item, "amount").toPlainString());
        }
        return simplePdf(lines);
    }

    public byte[] pdfForTenant(Long invoiceId, String tenantId, boolean superAdmin) {
        invoiceForTenant(invoiceId, tenantId, superAdmin);
        return pdf(invoiceId);
    }

    public byte[] excel(Long invoiceId) {
        Map<String, Object> invoice = invoice(invoiceId);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ")
                .append("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"><Worksheet ss:Name=\"Invoice\">")
                .append("<Table><Row><Cell><Data ss:Type=\"String\">Resource</Data></Cell>")
                .append("<Cell><Data ss:Type=\"String\">Description</Data></Cell>")
                .append("<Cell><Data ss:Type=\"String\">Quantity</Data></Cell>")
                .append("<Cell><Data ss:Type=\"String\">Amount</Data></Cell></Row>");
        for (Object itemValue : (List<?>) invoice.get("items")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) itemValue;
            xml.append("<Row>")
                    .append(cell(BillingValues.rowString(item, "resource_code"), "String"))
                    .append(cell(BillingValues.rowString(item, "description"), "String"))
                    .append(cell(BillingValues.rowDecimal(item, "quantity").toPlainString(), "Number"))
                    .append(cell(BillingValues.rowDecimal(item, "amount").toPlainString(), "Number"))
                    .append("</Row>");
        }
        xml.append("</Table></Worksheet></Workbook>");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] excelForTenant(Long invoiceId, String tenantId, boolean superAdmin) {
        invoiceForTenant(invoiceId, tenantId, superAdmin);
        return excel(invoiceId);
    }

    @Scheduled(cron = "${core.billing.invoice.generate-cron:0 30 1 1 * *}")
    public void scheduledGenerate() {
        String period = YearMonth.now().minusMonths(1).toString();
        Set<String> tenants = new LinkedHashSet<>();
        for (Map<String, Object> subscription : store.list("subscription", Map.of(), 1, 5000)) {
            tenants.add(BillingValues.rowString(subscription, "tenant_id"));
        }
        for (String tenant : tenants) {
            try {
                generate(map("tenantId", tenant, "period", period), tenant, "invoice-job");
            } catch (Exception exception) {
                log.error("自动生成账单失败: tenant={}, period={}", tenant, period, exception);
            }
        }
    }

    private List<Map<String, Object>> buildItems(String tenantId, String period) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Map<String, Object>> usageItems = new LinkedHashMap<>();
        for (Map<String, Object> usage : store.list("usageRecord", map(
                "tenant_id", tenantId,
                "period", period), 1, 5000)) {
            String resource = BillingValues.rowString(usage, "resource_code");
            Map<String, Object> item = usageItems.computeIfAbsent(resource, key -> map(
                    "item_type", "USAGE",
                    "resource_code", key,
                    "description", key + " usage",
                    "quantity", BigDecimal.ZERO,
                    "unit_price", BigDecimal.ZERO,
                    "amount", BigDecimal.ZERO));
            item.put("quantity", BillingValues.decimal(item.get("quantity"))
                    .add(BillingValues.rowDecimal(usage, "quantity")));
            item.put("amount", BillingValues.decimal(item.get("amount"))
                    .add(BillingValues.rowDecimal(usage, "cost")));
        }
        for (Map<String, Object> item : usageItems.values()) {
            BigDecimal quantity = BillingValues.decimal(item.get("quantity"));
            BigDecimal amount = BillingValues.decimal(item.get("amount"));
            item.put("unit_price", quantity.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : amount.divide(quantity, 6, RoundingMode.HALF_UP));
            result.add(item);
        }

        store.list("subscription", Map.of("tenant_id", tenantId), 1, 5000).stream()
                .filter(subscription -> List.of("ACTIVE", "CHANGED", "CANCELLED")
                        .contains(BillingValues.rowString(subscription, "status")))
                .filter(subscription -> YearMonth.from(toDateTime(
                        subscription.get("start_time"))).toString().compareTo(period) <= 0)
                .findFirst()
                .ifPresent(subscription -> {
                    Map<String, Object> plan = store.findById(
                            "plan", BillingValues.rowLong(subscription, "plan_id")).orElseThrow();
                    BigDecimal price = BillingValues.rowDecimal(plan, "price");
                    if (price.compareTo(BigDecimal.ZERO) > 0) {
                        result.add(map(
                                "item_type", "SUBSCRIPTION",
                                "resource_code", "PLAN_" + BillingValues.rowString(plan, "plan_code"),
                                "description", BillingValues.rowString(plan, "plan_name"),
                                "quantity", BigDecimal.ONE,
                                "unit_price", price,
                                "amount", price));
                    }
                });
        return result;
    }

    private void generateStatement(String tenantId, YearMonth period, String actor) {
        Map<String, Object> filters = map(
                "tenant_id", tenantId,
                "period", period.toString());
        if (store.findOne("statement", filters).isPresent()) {
            return;
        }
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.plusMonths(1).atDay(1).atStartOfDay();
        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        List<Account> accounts = accountRepository.findByTenant(tenantId, 1, 5000);
        for (Account account : accounts) {
            int count = transactionRepository.countByAccountId(account.getId());
            for (Transaction transaction : transactionRepository.findByAccountId(
                    account.getId(), 1, Math.max(count, 1))) {
                if (transaction.getCreateTime() == null) {
                    continue;
                }
                BigDecimal signed = transaction.getDirection() == Direction.IN
                        ? transaction.getAmount() : transaction.getAmount().negate();
                if (transaction.getCreateTime().isBefore(start)) {
                    opening = opening.add(signed);
                } else if (transaction.getCreateTime().isBefore(end)) {
                    if (transaction.getDirection() == Direction.IN) {
                        totalIn = totalIn.add(transaction.getAmount());
                    } else {
                        totalOut = totalOut.add(transaction.getAmount());
                    }
                }
            }
        }
        store.insert("statement", map(
                "tenant_id", tenantId,
                "period", period.toString(),
                "opening_balance", opening,
                "closing_balance", opening.add(totalIn).subtract(totalOut),
                "total_in", totalIn,
                "total_out", totalOut,
                "currency", "CNY"), actor);
    }

    private BigDecimal defaultTaxRate(String country) {
        return store.findOne("taxRule", map(
                        "country", country.toUpperCase(),
                        "status", "ACTIVE"))
                .map(rule -> BillingValues.rowDecimal(rule, "rate"))
                .orElse(BigDecimal.ZERO);
    }

    private byte[] simplePdf(List<String> lines) {
        StringBuilder content = new StringBuilder("BT /F1 12 Tf 50 790 Td ");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                content.append("0 -20 Td ");
            }
            content.append("(").append(pdfEscape(lines.get(i))).append(") Tj ");
        }
        content.append("ET");
        byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);
        List<byte[]> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.US_ASCII),
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".getBytes(StandardCharsets.US_ASCII),
                ("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                        "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>")
                        .getBytes(StandardCharsets.US_ASCII),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
                        .getBytes(StandardCharsets.US_ASCII),
                ("<< /Length " + stream.length + " >>\nstream\n" +
                        new String(stream, StandardCharsets.US_ASCII) + "\nendstream")
                        .getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n");
            out.writeBytes(objects.get(i));
            write(out, "\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n");
        write(out, "0000000000 65535 f \n");
        for (Integer offset : offsets) {
            write(out, String.format("%010d 00000 n \n", offset));
        }
        write(out, "trailer << /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n");
        write(out, "startxref\n" + xref + "\n%%EOF");
        return out.toByteArray();
    }

    private void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }

    private String pdfEscape(String value) {
        return ascii(value).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String ascii(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String cell(String value, String type) {
        return "<Cell><Data ss:Type=\"" + type + "\">" + xml(value) + "</Data></Cell>";
    }

    private String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private LocalDateTime toDateTime(Object value) {
        return BillingValues.dateTime(value);
    }
}
