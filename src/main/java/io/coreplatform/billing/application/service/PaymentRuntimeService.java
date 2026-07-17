package io.coreplatform.billing.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.billing.application.command.RecordTransactionCommand;
import io.coreplatform.billing.application.exception.BillingBusinessException;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import io.coreplatform.billing.application.port.PaymentDriver;
import io.coreplatform.billing.application.support.BillingValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.coreplatform.billing.application.support.BillingValues.map;

@Service
public class PaymentRuntimeService {

    private final BillingRuntimeStore store;
    private final TransactionService transactionService;
    private final SubscriptionRuntimeService subscriptionService;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;
    private final Map<String, PaymentDriver> drivers;

    public PaymentRuntimeService(BillingRuntimeStore store,
                                 TransactionService transactionService,
                                 SubscriptionRuntimeService subscriptionService,
                                 AccountService accountService,
                                 ObjectMapper objectMapper,
                                 List<PaymentDriver> drivers) {
        this.store = store;
        this.transactionService = transactionService;
        this.subscriptionService = subscriptionService;
        this.accountService = accountService;
        this.objectMapper = objectMapper;
        this.drivers = drivers.stream().collect(Collectors.toUnmodifiableMap(
                driver -> driver.code().toUpperCase(), Function.identity()));
    }

    public List<Map<String, Object>> channels() {
        return store.list("paymentChannel", Map.of(), 1, 1000);
    }

    @Transactional
    public Map<String, Object> createChannel(Map<String, Object> request, String actor) {
        String code = BillingValues.requiredString(request, "channelCode").toUpperCase();
        String driverCode = BillingValues.string(request, "driverCode", code).toUpperCase();
        requireDriver(driverCode);
        store.findOne("paymentChannel", Map.of("channel_code", code)).ifPresent(existing -> {
            throw BillingBusinessException.conflict(
                    "BILLING_PAYMENT_CHANNEL_EXISTS", "支付渠道已存在: " + code);
        });
        long id = store.insert("paymentChannel", map(
                "channel_code", code,
                "channel_name", BillingValues.requiredString(request, "channelName"),
                "driver_code", driverCode,
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase(),
                "config_ref", BillingValues.string(request, "configRef", "")), actor);
        return store.findById("paymentChannel", id).orElseThrow();
    }

    @Transactional
    public Map<String, Object> createOrder(Map<String, Object> request,
                                           String tenantId,
                                           String actor) {
        String idempotencyKey = BillingValues.requiredString(request, "idempotencyKey");
        var existing = store.findOne("paymentOrder", Map.of("idempotency_key", idempotencyKey));
        if (existing.isPresent()) {
            return existing.get();
        }
        String orderTenant = BillingValues.string(request, "tenantId", tenantId);
        String channelCode = BillingValues.string(request, "channelCode", "MOCK").toUpperCase();
        Map<String, Object> channel = store.findOne("paymentChannel", Map.of(
                        "channel_code", channelCode, "status", "ACTIVE"))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_PAYMENT_CHANNEL_NOT_FOUND", "支付渠道不可用: " + channelCode));
        PaymentDriver driver = requireDriver(BillingValues.rowString(channel, "driver_code"));
        String businessType = BillingValues.requiredString(request, "businessType").toUpperCase();
        if (!List.of("TOP_UP", "SUBSCRIPTION", "INVOICE").contains(businessType)) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_PAYMENT_BUSINESS_INVALID",
                    "businessType 仅支持 TOP_UP / SUBSCRIPTION / INVOICE");
        }
        BigDecimal amount = BillingValues.positive(request, "amount");
        Long accountId = BillingValues.longValue(request.get("accountId"));
        Long planId = BillingValues.longValue(request.get("planId"));
        if ("TOP_UP".equals(businessType) && accountId == null) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_PAYMENT_ACCOUNT_REQUIRED", "充值订单必须提供 accountId");
        }
        if ("TOP_UP".equals(businessType)) {
            accountService.getAuthorizedAccount(accountId, orderTenant, false);
        }
        if ("SUBSCRIPTION".equals(businessType) && planId == null) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_PAYMENT_PLAN_REQUIRED", "订阅订单必须提供 planId");
        }
        if ("SUBSCRIPTION".equals(businessType)) {
            Map<String, Object> plan = store.findById("plan", planId)
                    .orElseThrow(() -> BillingBusinessException.notFound(
                            "BILLING_PLAN_NOT_FOUND", "支付套餐不存在"));
            if (!"ACTIVE".equals(BillingValues.rowString(plan, "status")) ||
                    BillingValues.rowDecimal(plan, "price").compareTo(amount) != 0) {
                throw BillingBusinessException.conflict(
                        "BILLING_PAYMENT_PLAN_AMOUNT_MISMATCH", "支付金额与有效套餐价格不一致");
            }
        }
        if ("INVOICE".equals(businessType)) {
            Long invoiceId;
            try {
                invoiceId = Long.valueOf(BillingValues.requiredString(request, "businessId"));
            } catch (NumberFormatException exception) {
                throw BillingBusinessException.unprocessable(
                        "BILLING_PAYMENT_INVOICE_INVALID", "账单 businessId 必须为数字 ID");
            }
            Map<String, Object> invoice = store.findById("invoice", invoiceId)
                    .orElseThrow(() -> BillingBusinessException.notFound(
                            "BILLING_INVOICE_NOT_FOUND", "支付账单不存在"));
            if (!orderTenant.equals(BillingValues.rowString(invoice, "tenant_id")) ||
                    BillingValues.rowDecimal(invoice, "total").compareTo(amount) != 0) {
                throw BillingBusinessException.conflict(
                        "BILLING_PAYMENT_INVOICE_AMOUNT_MISMATCH", "支付金额或租户与账单不一致");
            }
        }
        String orderNo = BillingValues.number("PO");
        long id = store.insert("paymentOrder", map(
                "order_no", orderNo,
                "tenant_id", orderTenant,
                "business_type", businessType,
                "business_id", BillingValues.string(request, "businessId",
                        planId == null ? String.valueOf(accountId) : String.valueOf(planId)),
                "account_id", accountId,
                "plan_id", planId,
                "amount", amount,
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "status", "CREATED",
                "channel_code", channelCode,
                "idempotency_key", idempotencyKey,
                "provider_trade_no", "",
                "failure_reason", ""), actor);
        Map<String, Object> driverResult = driver.create(
                orderNo, amount, BillingValues.string(request, "currency", "CNY").toUpperCase());
        store.update("paymentOrder", id, map(
                "status", "PENDING",
                "provider_trade_no", driverResult.get("providerTradeNo")), actor);
        paymentLog(orderNo, "CREATE", json(driverResult), actor);
        Map<String, Object> result = store.findById("paymentOrder", id).orElseThrow();
        result.put("paymentUrl", driverResult.get("paymentUrl"));
        return result;
    }

    @Transactional
    public Map<String, Object> callback(Map<String, Object> request,
                                        String signature,
                                        String actor) {
        String callbackId = BillingValues.requiredString(request, "callbackId");
        var existing = store.findOne("paymentCallback", Map.of("callback_id", callbackId));
        if (existing.isPresent()) {
            return callbackEnvelope(existing.get());
        }
        String orderNo = BillingValues.requiredString(request, "orderNo");
        Map<String, Object> order = order(orderNo);
        String eventType = BillingValues.requiredString(request, "eventType").toUpperCase();
        BigDecimal amount = BillingValues.positive(request, "amount");
        String payload = signedPayload(callbackId, orderNo, eventType, amount);
        PaymentDriver driver = driverForOrder(order);
        if (!driver.verify(payload, signature)) {
            throw BillingBusinessException.forbidden(
                    "BILLING_PAYMENT_SIGNATURE_INVALID", "支付回调签名无效");
        }
        if (BillingValues.rowDecimal(order, "amount").compareTo(amount) != 0) {
            throw BillingBusinessException.conflict(
                    "BILLING_PAYMENT_AMOUNT_MISMATCH", "支付回调金额与订单不一致");
        }

        long callbackPk = store.insert("paymentCallback", map(
                "callback_id", callbackId,
                "order_no", orderNo,
                "channel_code", BillingValues.rowString(order, "channel_code"),
                "event_type", eventType,
                "amount", amount,
                "payload", json(request),
                "signature", signature,
                "status", "VERIFIED",
                "processed_time", Timestamp.valueOf(LocalDateTime.now()),
                "error_message", ""), actor);

        if ("SUCCESS".equals(eventType)) {
            processSuccess(order, request, actor);
        } else if ("FAILED".equals(eventType)) {
            store.update("paymentOrder", BillingValues.rowLong(order, "id"), map(
                    "status", "FAILED",
                    "failure_reason", BillingValues.string(request, "reason", "渠道支付失败")), actor);
        }
        paymentLog(orderNo, "CALLBACK_" + eventType, json(request), actor);
        return callbackEnvelope(store.findById("paymentCallback", callbackPk).orElseThrow());
    }

    @Transactional
    public Map<String, Object> simulateSuccess(String orderNo, String actor) {
        Map<String, Object> order = order(orderNo);
        PaymentDriver driver = driverForOrder(order);
        Map<String, Object> request = map(
                "callbackId", BillingValues.number("CB"),
                "orderNo", orderNo,
                "eventType", "SUCCESS",
                "amount", BillingValues.rowDecimal(order, "amount"),
                "providerTradeNo", BillingValues.rowString(order, "provider_trade_no"));
        String payload = signedPayload(
                BillingValues.string(request.get("callbackId")),
                orderNo,
                "SUCCESS",
                BillingValues.rowDecimal(order, "amount"));
        return callback(request, driver.sign(payload), actor);
    }

    @Transactional
    public Map<String, Object> refund(String orderNo,
                                      Map<String, Object> request,
                                      String actor) {
        Map<String, Object> order = order(orderNo);
        if (!"SUCCESS".equals(BillingValues.rowString(order, "status"))) {
            throw BillingBusinessException.conflict(
                    "BILLING_PAYMENT_NOT_REFUNDABLE", "只有成功订单可以退款");
        }
        BigDecimal orderAmount = BillingValues.rowDecimal(order, "amount");
        BigDecimal amount = request.get("amount") == null
                ? orderAmount : BillingValues.positive(request, "amount");
        if (amount.compareTo(orderAmount) != 0) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_PARTIAL_REFUND_UNSUPPORTED", "MVP 只支持全额退款");
        }
        String refundNo = BillingValues.number("RF");
        Map<String, Object> driverResult = driverForOrder(order).refund(orderNo, refundNo, amount);
        long id = store.insert("refund", map(
                "refund_no", refundNo,
                "payment_order_id", BillingValues.rowLong(order, "id"),
                "amount", amount,
                "status", BillingValues.string(driverResult.get("status")).toUpperCase(),
                "reason", BillingValues.requiredString(request, "reason"),
                "provider_refund_no", driverResult.get("providerRefundNo")), actor);
        if ("SUCCESS".equals(BillingValues.string(driverResult.get("status")).toUpperCase())) {
            processRefund(order, refundNo, amount, actor);
            store.update("paymentOrder", BillingValues.rowLong(order, "id"),
                    Map.of("status", "REFUNDED"), actor);
        }
        paymentLog(orderNo, "REFUND", json(driverResult), actor);
        return store.findById("refund", id).orElseThrow();
    }

    @Transactional
    public List<Map<String, Object>> reconcile(String date, String actor) {
        String target = date == null || date.isBlank() ? LocalDate.now().toString() : date;
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (Map<String, Object> order : store.list("paymentOrder", Map.of(), 1, 5000)) {
            if (!toDate(order.get("create_time")).toString().equals(target)) {
                continue;
            }
            String orderNo = BillingValues.rowString(order, "order_no");
            List<Map<String, Object>> callbacks = store.list(
                    "paymentCallback", Map.of("order_no", orderNo, "status", "VERIFIED"), 1, 1000);
            BigDecimal channelAmount = callbacks.stream()
                    .filter(row -> "SUCCESS".equals(BillingValues.rowString(row, "event_type")))
                    .map(row -> BillingValues.rowDecimal(row, "amount"))
                    .findFirst().orElse(BigDecimal.ZERO);
            BigDecimal localAmount = BillingValues.rowDecimal(order, "amount");
            String result = "SUCCESS";
            if ("SUCCESS".equals(BillingValues.rowString(order, "status")) &&
                    channelAmount.compareTo(BigDecimal.ZERO) == 0) {
                result = "MISSING";
            } else if (channelAmount.compareTo(BigDecimal.ZERO) > 0 &&
                    localAmount.compareTo(channelAmount) != 0) {
                result = "AMOUNT_NOT_MATCH";
            }
            long id = store.insert("reconciliation", map(
                    "reconcile_date", target,
                    "order_no", orderNo,
                    "local_amount", localAmount,
                    "channel_amount", channelAmount,
                    "result", result,
                    "detail", "自动对账"), actor);
            results.add(store.findById("reconciliation", id).orElseThrow());
        }
        return results;
    }

    public List<Map<String, Object>> orders(String tenantId) {
        return store.list("paymentOrder",
                tenantId == null || tenantId.isBlank()
                        ? Map.of() : Map.of("tenant_id", tenantId), 1, 5000);
    }

    public List<Map<String, Object>> callbacks() {
        return store.list("paymentCallback", Map.of(), 1, 5000);
    }

    public List<Map<String, Object>> refunds() {
        return store.list("refund", Map.of(), 1, 5000);
    }

    private void processSuccess(Map<String, Object> order,
                                Map<String, Object> request,
                                String actor) {
        if ("SUCCESS".equals(BillingValues.rowString(order, "status"))) {
            return;
        }
        store.update("paymentOrder", BillingValues.rowLong(order, "id"), map(
                "status", "SUCCESS",
                "provider_trade_no", BillingValues.string(
                        request, "providerTradeNo",
                        BillingValues.rowString(order, "provider_trade_no")),
                "paid_time", Timestamp.valueOf(LocalDateTime.now()),
                "failure_reason", ""), actor);
        String businessType = BillingValues.rowString(order, "business_type");
        if ("TOP_UP".equals(businessType)) {
            RecordTransactionCommand command = new RecordTransactionCommand();
            command.setAccountId(BillingValues.rowLong(order, "account_id"));
            command.setType("TOP_UP");
            command.setAmount(BillingValues.rowDecimal(order, "amount"));
            command.setReferenceType("PAYMENT_ORDER");
            command.setReferenceId(BillingValues.rowString(order, "order_no"));
            command.setDescription("支付充值");
            transactionService.recordTransaction(command, actor);
        } else if ("SUBSCRIPTION".equals(businessType)) {
            Map<String, Object> plan = store.findById(
                    "plan", BillingValues.rowLong(order, "plan_id")).orElseThrow();
            subscriptionService.subscribe(
                    Map.of("plan", BillingValues.rowString(plan, "plan_code")),
                    BillingValues.rowString(order, "tenant_id"),
                    actor);
        } else if ("INVOICE".equals(businessType)) {
            Long invoiceId = Long.valueOf(BillingValues.rowString(order, "business_id"));
            Map<String, Object> invoice = store.findById("invoice", invoiceId)
                    .orElseThrow(() -> BillingBusinessException.notFound(
                            "BILLING_INVOICE_NOT_FOUND", "支付关联账单不存在"));
            store.update("invoice", invoiceId, Map.of("status", "PAID"), actor);
            store.insert("settlement", map(
                    "settlement_no", BillingValues.number("ST"),
                    "invoice_id", invoiceId,
                    "payment_order_id", BillingValues.rowLong(order, "id"),
                    "amount", BillingValues.rowDecimal(invoice, "total"),
                    "status", "SUCCESS",
                    "settled_time", Timestamp.valueOf(LocalDateTime.now())), actor);
        }
    }

    private void processRefund(Map<String, Object> order,
                               String refundNo,
                               BigDecimal amount,
                               String actor) {
        String businessType = BillingValues.rowString(order, "business_type");
        if ("TOP_UP".equals(businessType)) {
            RecordTransactionCommand command = new RecordTransactionCommand();
            command.setAccountId(BillingValues.rowLong(order, "account_id"));
            command.setType("PAYMENT_REFUND");
            command.setAmount(amount);
            command.setReferenceType("PAYMENT_REFUND");
            command.setReferenceId(refundNo);
            command.setDescription("支付退款回收充值余额");
            transactionService.recordTransaction(command, actor);
        } else if ("SUBSCRIPTION".equals(businessType)) {
            subscriptionService.lifecycle(
                    "cancel", BillingValues.rowString(order, "tenant_id"), actor);
        }
    }

    private Map<String, Object> order(String orderNo) {
        return store.findOne("paymentOrder", Map.of("order_no", orderNo))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_PAYMENT_ORDER_NOT_FOUND", "支付订单不存在: " + orderNo));
    }

    private PaymentDriver driverForOrder(Map<String, Object> order) {
        Map<String, Object> channel = store.findOne("paymentChannel", Map.of(
                        "channel_code", BillingValues.rowString(order, "channel_code")))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_PAYMENT_CHANNEL_NOT_FOUND", "订单支付渠道不存在"));
        return requireDriver(BillingValues.rowString(channel, "driver_code"));
    }

    private PaymentDriver requireDriver(String code) {
        PaymentDriver driver = drivers.get(code.toUpperCase());
        if (driver == null) {
            throw BillingBusinessException.notFound(
                    "BILLING_PAYMENT_DRIVER_NOT_FOUND", "支付驱动不存在: " + code);
        }
        return driver;
    }

    private Map<String, Object> callbackEnvelope(Map<String, Object> callback) {
        Map<String, Object> result = new LinkedHashMap<>(callback);
        result.put("order", order(BillingValues.rowString(callback, "order_no")));
        return result;
    }

    private String signedPayload(String callbackId, String orderNo,
                                 String eventType, BigDecimal amount) {
        return callbackId + "|" + orderNo + "|" + eventType + "|" + amount.stripTrailingZeros().toPlainString();
    }

    private void paymentLog(String orderNo, String operation, String detail, String actor) {
        store.insert("paymentLog", map(
                "order_no", orderNo,
                "operation", operation,
                "detail", detail), actor);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private LocalDate toDate(Object value) {
        return BillingValues.dateTime(value).toLocalDate();
    }
}
