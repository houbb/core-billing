package io.coreplatform.billing.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.billing.application.exception.BillingBusinessException;
import io.coreplatform.billing.application.port.BillingRuntimeStore;
import io.coreplatform.billing.application.support.BillingValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.coreplatform.billing.application.support.BillingValues.map;

@Service
public class EnterpriseRuntimeService {

    private static final Map<String, String> MODULE_ENTITIES = Map.ofEntries(
            Map.entry("contracts", "contract"),
            Map.entry("organizations", "organization"),
            Map.entry("currency-rates", "currencyRate"),
            Map.entry("campaigns", "campaign"),
            Map.entry("coupons", "coupon"),
            Map.entry("discounts", "discount"),
            Map.entry("listings", "listing"),
            Map.entry("marketplace-orders", "marketplaceOrder"),
            Map.entry("partners", "partner"),
            Map.entry("partner-orders", "partnerOrder"),
            Map.entry("commissions", "commission"),
            Map.entry("budgets", "budget"),
            Map.entry("budget-alerts", "budgetAlert"),
            Map.entry("cost-centers", "costCenter"),
            Map.entry("cost-allocations", "costAllocation"),
            Map.entry("approvals", "approval"),
            Map.entry("revenue-shares", "revenueShare"),
            Map.entry("payouts", "payout"));

    private final BillingRuntimeStore store;
    private final ObjectMapper objectMapper;

    public EnterpriseRuntimeService(BillingRuntimeStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> list(String module, String tenantId) {
        String entity = entity(module);
        Map<String, Object> filters = tenantScoped(entity) && tenantId != null && !tenantId.isBlank()
                ? Map.of("tenant_id", tenantId) : Map.of();
        return store.list(entity, filters, 1, 5000);
    }

    @Transactional
    public Map<String, Object> create(String module,
                                      Map<String, Object> request,
                                      String tenantId,
                                      String actor) {
        return switch (module) {
            case "contracts" -> createContract(request, tenantId, actor);
            case "organizations" -> createOrganization(request, tenantId, actor);
            case "currency-rates" -> createCurrencyRate(request, actor);
            case "campaigns" -> createCampaign(request, actor);
            case "coupons" -> createCoupon(request, actor);
            case "listings" -> createListing(request, actor);
            case "marketplace-orders" -> createMarketplaceOrder(request, tenantId, actor);
            case "partners" -> createPartner(request, actor);
            case "partner-orders" -> createPartnerOrder(request, tenantId, actor);
            case "budgets" -> createBudget(request, tenantId, actor);
            case "cost-centers" -> createCostCenter(request, tenantId, actor);
            case "cost-allocations" -> createCostAllocation(request, actor);
            case "approvals" -> createApproval(request, tenantId, actor);
            case "revenue-shares" -> createRevenueShare(request, actor);
            case "payouts" -> createPayout(request, actor);
            default -> throw BillingBusinessException.unprocessable(
                    "BILLING_ENTERPRISE_MODULE_READ_ONLY",
                    module + " 不支持直接创建");
        };
    }

    @Transactional
    public Map<String, Object> contractAction(Long contractId, String action,
                                              String tenantId, boolean superAdmin, String actor) {
        Map<String, Object> contract = require("contract", contractId, "合同");
        requireTenant(contract, tenantId, superAdmin, "合同");
        String current = BillingValues.rowString(contract, "status");
        String next = switch (action.toUpperCase()) {
            case "SUBMIT" -> "PENDING_APPROVAL";
            case "APPROVE" -> "ACTIVE";
            case "RENEW" -> "RENEWED";
            case "TERMINATE" -> "TERMINATED";
            default -> throw BillingBusinessException.unprocessable(
                    "BILLING_CONTRACT_ACTION_INVALID", "合同动作不受支持");
        };
        if ("TERMINATED".equals(current)) {
            throw BillingBusinessException.conflict(
                    "BILLING_CONTRACT_TERMINATED", "已终止合同不能继续流转");
        }
        store.update("contract", contractId, Map.of("status", next), actor);
        return require("contract", contractId, "合同");
    }

    public Map<String, Object> convert(Map<String, Object> request) {
        String from = BillingValues.requiredString(request, "fromCurrency").toUpperCase();
        String to = BillingValues.requiredString(request, "toCurrency").toUpperCase();
        BigDecimal amount = BillingValues.positive(request, "amount");
        if (from.equals(to)) {
            return map("from", from, "to", to, "amount", amount, "rate", BigDecimal.ONE,
                    "convertedAmount", amount);
        }
        Map<String, Object> rate = currentRate(from, to);
        BigDecimal value = amount.multiply(BillingValues.rowDecimal(rate, "rate"))
                .setScale(6, RoundingMode.HALF_UP);
        return map(
                "from", from,
                "to", to,
                "amount", amount,
                "rate", BillingValues.rowDecimal(rate, "rate"),
                "convertedAmount", value,
                "effectiveTime", rate.get("effective_time"));
    }

    @Transactional
    public Map<String, Object> redeemCoupon(Map<String, Object> request, String actor) {
        String code = BillingValues.requiredString(request, "couponCode").toUpperCase();
        BigDecimal original = BillingValues.positive(request, "amount");
        String referenceType = BillingValues.requiredString(request, "referenceType").toUpperCase();
        String referenceId = BillingValues.requiredString(request, "referenceId");
        var existingDiscount = store.findOne("discount", map(
                "reference_type", referenceType,
                "reference_id", referenceId));
        if (existingDiscount.isPresent()) {
            return existingDiscount.get();
        }
        Map<String, Object> coupon = store.findOne("coupon", Map.of("coupon_code", code))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_COUPON_NOT_FOUND", "优惠券不存在"));
        if (!store.incrementCouponUsage(BillingValues.rowLong(coupon, "id"), actor)) {
            throw BillingBusinessException.conflict(
                    "BILLING_COUPON_UNAVAILABLE", "优惠券已失效或达到使用上限");
        }
        BigDecimal value = BillingValues.rowDecimal(coupon, "discount_value");
        BigDecimal discount = "PERCENT".equals(BillingValues.rowString(coupon, "discount_type"))
                ? original.multiply(value).setScale(6, RoundingMode.HALF_UP)
                : value;
        discount = discount.min(original);
        long id = store.insert("discount", map(
                "reference_type", referenceType,
                "reference_id", referenceId,
                "coupon_id", BillingValues.rowLong(coupon, "id"),
                "original_amount", original,
                "discount_amount", discount,
                "final_amount", original.subtract(discount),
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase()), actor);
        return store.findById("discount", id).orElseThrow();
    }

    @Transactional
    public Map<String, Object> budgetCheck(Map<String, Object> request,
                                           String tenantId,
                                           String actor) {
        String scopeType = BillingValues.requiredString(request, "scopeType").toUpperCase();
        String scopeId = BillingValues.requiredString(request, "scopeId");
        String resource = BillingValues.string(request, "resourceCode", "").toUpperCase();
        String period = BillingValues.string(request, "period", BillingValues.month());
        BigDecimal amount = BillingValues.positive(request, "amount");
        Map<String, Object> budget = store.findOne("budget", map(
                        "tenant_id", tenantId,
                        "scope_type", scopeType,
                        "scope_id", scopeId,
                        "resource_code", resource,
                        "period", period,
                        "status", "ACTIVE"))
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_BUDGET_NOT_FOUND", "未找到匹配预算"));
        BigDecimal projected = BillingValues.rowDecimal(budget, "used_amount").add(amount);
        BigDecimal total = BillingValues.rowDecimal(budget, "budget_amount");
        String policy = BillingValues.rowString(budget, "policy");
        if (projected.compareTo(total) > 0 && "BLOCK".equals(policy)) {
            createBudgetAlert(budget, "EXCEEDED", projected, actor);
            throw BillingBusinessException.conflict(
                    "BILLING_BUDGET_EXCEEDED", "预算已超限，策略为 BLOCK");
        }
        if (!store.mutateBudget(BillingValues.rowLong(budget, "id"), amount, actor)) {
            throw BillingBusinessException.conflict(
                    "BILLING_BUDGET_CONCURRENT_CHANGE", "预算并发状态已变化");
        }
        Map<String, Object> updated = require(
                "budget", BillingValues.rowLong(budget, "id"), "预算");
        BigDecimal ratio = BillingValues.percent(
                BillingValues.rowDecimal(updated, "used_amount"), total)
                .multiply(new BigDecimal("100"));
        BigDecimal threshold = BillingValues.rowDecimal(updated, "warning_threshold");
        if (ratio.compareTo(new BigDecimal("100")) >= 0) {
            createBudgetAlert(updated, "EXCEEDED",
                    BillingValues.rowDecimal(updated, "used_amount"), actor);
        } else if (ratio.compareTo(threshold) >= 0) {
            createBudgetAlert(updated, "WARNING",
                    BillingValues.rowDecimal(updated, "used_amount"), actor);
        }
        return map(
                "allowed", true,
                "budget", updated,
                "remaining", total.subtract(
                        BillingValues.rowDecimal(updated, "used_amount")).max(BigDecimal.ZERO),
                "policy", policy);
    }

    @Transactional
    public Map<String, Object> approvalAction(Long approvalId,
                                              String action,
                                              Map<String, Object> request,
                                              String tenantId,
                                              boolean superAdmin,
                                              String actor) {
        Map<String, Object> approval = require("approval", approvalId, "审批");
        requireTenant(approval, tenantId, superAdmin, "审批");
        String status = BillingValues.rowString(approval, "status");
        String normalized = action.toUpperCase();
        Map<String, Object> update = switch (normalized) {
            case "REVIEW" -> {
                requireStatus(status, List.of("APPLIED"), "审批");
                yield map("status", "REVIEWING", "reviewer", actor, "current_step", 2);
            }
            case "APPROVE" -> {
                requireStatus(status, List.of("APPLIED", "REVIEWING"), "审批");
                yield map(
                        "status", "APPROVED",
                        "reviewer", actor,
                        "current_step", 3,
                        "decision_comment", BillingValues.string(request, "comment", ""));
            }
            case "REJECT" -> {
                requireStatus(status, List.of("APPLIED", "REVIEWING"), "审批");
                yield map(
                        "status", "REJECTED",
                        "reviewer", actor,
                        "decision_comment", BillingValues.requiredString(request, "comment"));
            }
            case "EXECUTE" -> {
                requireStatus(status, List.of("APPROVED"), "审批");
                yield map("status", "EXECUTED", "current_step", 4);
            }
            default -> throw BillingBusinessException.unprocessable(
                    "BILLING_APPROVAL_ACTION_INVALID", "审批动作不受支持");
        };
        store.update("approval", approvalId, update, actor);
        return require("approval", approvalId, "审批");
    }

    @Transactional
    public Map<String, Object> payoutBeneficiary(Map<String, Object> request, String actor) {
        String type = BillingValues.requiredString(request, "beneficiaryType").toUpperCase();
        String id = BillingValues.requiredString(request, "beneficiaryId");
        List<Map<String, Object>> shares = store.list("revenueShare", map(
                "beneficiary_type", type,
                "beneficiary_id", id,
                "status", "PENDING"), 1, 5000);
        BigDecimal amount = shares.stream()
                .map(row -> BillingValues.rowDecimal(row, "share_amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BillingBusinessException.conflict(
                    "BILLING_PAYOUT_EMPTY", "没有待打款分账");
        }
        long payoutId = store.insert("payout", map(
                "payout_no", BillingValues.number("PY"),
                "beneficiary_type", type,
                "beneficiary_id", id,
                "amount", amount,
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "status", "SUCCESS",
                "paid_time", Timestamp.valueOf(LocalDateTime.now())), actor);
        for (Map<String, Object> share : shares) {
            store.update("revenueShare", BillingValues.rowLong(share, "id"),
                    Map.of("status", "PAID"), actor);
        }
        return store.findById("payout", payoutId).orElseThrow();
    }

    public Map<String, Object> dashboard(String tenantId) {
        BigDecimal budget = store.sum("budget", "budget_amount",
                tenantId == null || tenantId.isBlank() ? Map.of() : Map.of("tenant_id", tenantId));
        BigDecimal budgetUsed = store.sum("budget", "used_amount",
                tenantId == null || tenantId.isBlank() ? Map.of() : Map.of("tenant_id", tenantId));
        BigDecimal contractAmount = store.sum("contract", "amount",
                tenantId == null || tenantId.isBlank() ? Map.of() : Map.of("tenant_id", tenantId));
        BigDecimal pendingShare = store.sum("revenueShare", "share_amount",
                Map.of("status", "PENDING"));
        return map(
                "contracts", store.count("contract",
                        tenantId == null || tenantId.isBlank()
                                ? Map.of() : Map.of("tenant_id", tenantId)),
                "organizations", store.count("organization",
                        tenantId == null || tenantId.isBlank()
                                ? Map.of() : Map.of("tenant_id", tenantId)),
                "activeCampaigns", store.count("campaign", Map.of("status", "ACTIVE")),
                "marketplaceListings", store.count("listing", Map.of("status", "ACTIVE")),
                "partners", store.count("partner", Map.of("status", "ACTIVE")),
                "budget", budget,
                "budgetUsed", budgetUsed,
                "contractAmount", contractAmount,
                "pendingRevenueShare", pendingShare,
                "openApprovals", store.count("approval",
                        tenantId == null || tenantId.isBlank()
                                ? Map.of("status", "APPLIED")
                                : map("tenant_id", tenantId, "status", "APPLIED")));
    }

    private Map<String, Object> createContract(Map<String, Object> request,
                                               String tenantId,
                                               String actor) {
        LocalDateTime start = dateTime(request.get("startTime"), LocalDateTime.now());
        LocalDateTime end = dateTime(request.get("endTime"), start.plusYears(1));
        if (!end.isAfter(start)) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_CONTRACT_DATE_INVALID", "合同结束时间必须晚于开始时间");
        }
        long id = store.insert("contract", map(
                "contract_no", BillingValues.string(request, "contractNo",
                        BillingValues.number("CT")),
                "tenant_id", BillingValues.string(request, "tenantId", tenantId),
                "customer", BillingValues.requiredString(request, "customer"),
                "plan_id", BillingValues.longValue(request.get("planId")),
                "start_time", Timestamp.valueOf(start),
                "end_time", Timestamp.valueOf(end),
                "amount", BillingValues.positive(request, "amount"),
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "payment_term", BillingValues.string(request, "paymentTerm", "NET30").toUpperCase(),
                "status", BillingValues.string(request, "status", "DRAFT").toUpperCase(),
                "external_sign_id", BillingValues.string(request, "externalSignId", "")), actor);
        return require("contract", id, "合同");
    }

    private Map<String, Object> createOrganization(Map<String, Object> request,
                                                   String tenantId,
                                                   String actor) {
        String code = BillingValues.requiredString(request, "nodeCode").toUpperCase();
        String parentCode = BillingValues.string(request, "parentCode", "").toUpperCase();
        String path = "/" + code;
        if (!parentCode.isBlank()) {
            Map<String, Object> parent = store.findOne("organization", map(
                            "tenant_id", tenantId,
                            "node_code", parentCode))
                    .orElseThrow(() -> BillingBusinessException.notFound(
                            "BILLING_ORG_PARENT_NOT_FOUND", "父组织不存在: " + parentCode));
            path = BillingValues.rowString(parent, "path") + "/" + code;
        }
        long id = store.insert("organization", map(
                "tenant_id", BillingValues.string(request, "tenantId", tenantId),
                "node_code", code,
                "parent_code", parentCode,
                "node_type", BillingValues.string(request, "nodeType", "DEPARTMENT").toUpperCase(),
                "node_name", BillingValues.requiredString(request, "nodeName"),
                "path", path,
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase()), actor);
        return require("organization", id, "组织节点");
    }

    private Map<String, Object> createCurrencyRate(Map<String, Object> request, String actor) {
        BigDecimal rate = BillingValues.positive(request, "rate");
        long id = store.insert("currencyRate", map(
                "from_currency", BillingValues.requiredString(request, "fromCurrency").toUpperCase(),
                "to_currency", BillingValues.requiredString(request, "toCurrency").toUpperCase(),
                "rate", rate,
                "effective_time", Timestamp.valueOf(
                        dateTime(request.get("effectiveTime"), LocalDateTime.now())),
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase()), actor);
        return require("currencyRate", id, "汇率");
    }

    private Map<String, Object> createCampaign(Map<String, Object> request, String actor) {
        long id = store.insert("campaign", map(
                "campaign_code", BillingValues.requiredString(request, "campaignCode").toUpperCase(),
                "campaign_name", BillingValues.requiredString(request, "campaignName"),
                "campaign_type", BillingValues.string(request, "campaignType", "COUPON").toUpperCase(),
                "start_time", Timestamp.valueOf(dateTime(
                        request.get("startTime"), LocalDateTime.now())),
                "end_time", Timestamp.valueOf(dateTime(
                        request.get("endTime"), LocalDateTime.now().plusMonths(1))),
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase(),
                "rules_json", json(request.getOrDefault("rules", Map.of()))), actor);
        return require("campaign", id, "营销活动");
    }

    private Map<String, Object> createCoupon(Map<String, Object> request, String actor) {
        String type = BillingValues.string(request, "discountType", "PERCENT").toUpperCase();
        BigDecimal value = BillingValues.positive(request, "discountValue");
        if ("PERCENT".equals(type) && value.compareTo(BigDecimal.ONE) > 0) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_COUPON_PERCENT_INVALID", "百分比折扣必须在 0 到 1 之间");
        }
        long id = store.insert("coupon", map(
                "coupon_code", BillingValues.requiredString(request, "couponCode").toUpperCase(),
                "campaign_id", BillingValues.longValue(request.get("campaignId")),
                "discount_type", type,
                "discount_value", value,
                "usage_limit", BillingValues.intValue(request.get("usageLimit"), 1),
                "used_count", 0,
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase(),
                "expire_time", request.get("expireTime") == null ? null :
                        Timestamp.valueOf(dateTime(request.get("expireTime"), null))), actor);
        return require("coupon", id, "优惠券");
    }

    private Map<String, Object> createListing(Map<String, Object> request, String actor) {
        long id = store.insert("listing", map(
                "listing_code", BillingValues.requiredString(request, "listingCode").toUpperCase(),
                "creator_id", BillingValues.requiredString(request, "creatorId"),
                "listing_name", BillingValues.requiredString(request, "listingName"),
                "listing_type", BillingValues.string(request, "listingType", "PLUGIN").toUpperCase(),
                "price", BillingValues.positive(request, "price"),
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "platform_rate", request.get("platformRate") == null
                        ? new BigDecimal("0.20") : BillingValues.decimal(request.get("platformRate")),
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase()), actor);
        return require("listing", id, "市场商品");
    }

    private Map<String, Object> createMarketplaceOrder(Map<String, Object> request,
                                                       String tenantId,
                                                       String actor) {
        Long listingId = BillingValues.longValue(request.get("listingId"));
        Map<String, Object> listing = require("listing", listingId, "市场商品");
        BigDecimal gross = BillingValues.rowDecimal(listing, "price");
        String orderNo = BillingValues.string(request, "orderNo", BillingValues.number("MO"));
        long orderId = store.insert("marketplaceOrder", map(
                "order_no", orderNo,
                "listing_id", listingId,
                "tenant_id", BillingValues.string(request, "tenantId", tenantId),
                "buyer_id", BillingValues.requiredString(request, "buyerId"),
                "amount", gross,
                "currency", BillingValues.rowString(listing, "currency"),
                "status", BillingValues.string(request, "status", "PAID").toUpperCase(),
                "payment_order_id", BillingValues.longValue(request.get("paymentOrderId"))), actor);
        BigDecimal platformRate = BillingValues.rowDecimal(listing, "platform_rate");
        createShare("MARKETPLACE_ORDER", orderNo, "PLATFORM", "CORE_PLATFORM",
                gross, platformRate, BillingValues.rowString(listing, "currency"), actor);
        createShare("MARKETPLACE_ORDER", orderNo, "CREATOR",
                BillingValues.rowString(listing, "creator_id"),
                gross, BigDecimal.ONE.subtract(platformRate),
                BillingValues.rowString(listing, "currency"), actor);
        Map<String, Object> result = require("marketplaceOrder", orderId, "市场订单");
        result.put("revenueShares", store.list(
                "revenueShare", Map.of("source_id", orderNo), 1, 100));
        return result;
    }

    private Map<String, Object> createPartner(Map<String, Object> request, String actor) {
        long id = store.insert("partner", map(
                "partner_code", BillingValues.requiredString(request, "partnerCode").toUpperCase(),
                "partner_name", BillingValues.requiredString(request, "partnerName"),
                "partner_type", BillingValues.string(request, "partnerType", "RESELLER").toUpperCase(),
                "commission_rate", BillingValues.decimal(request.get("commissionRate")),
                "status", BillingValues.string(request, "status", "ACTIVE").toUpperCase()), actor);
        return require("partner", id, "伙伴");
    }

    private Map<String, Object> createPartnerOrder(Map<String, Object> request,
                                                   String tenantId,
                                                   String actor) {
        Long partnerId = BillingValues.longValue(request.get("partnerId"));
        Map<String, Object> partner = require("partner", partnerId, "伙伴");
        BigDecimal amount = BillingValues.positive(request, "amount");
        long orderId = store.insert("partnerOrder", map(
                "order_no", BillingValues.string(request, "orderNo", BillingValues.number("PAO")),
                "partner_id", partnerId,
                "tenant_id", BillingValues.string(request, "tenantId", tenantId),
                "business_type", BillingValues.requiredString(request, "businessType").toUpperCase(),
                "business_id", BillingValues.requiredString(request, "businessId"),
                "amount", amount,
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "status", "CONFIRMED"), actor);
        BigDecimal commission = amount.multiply(
                BillingValues.rowDecimal(partner, "commission_rate"))
                .setScale(6, RoundingMode.HALF_UP);
        long commissionId = store.insert("commission", map(
                "commission_no", BillingValues.number("CM"),
                "partner_id", partnerId,
                "partner_order_id", orderId,
                "amount", commission,
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "status", "PENDING"), actor);
        Map<String, Object> result = require("partnerOrder", orderId, "伙伴订单");
        result.put("commission", require("commission", commissionId, "佣金"));
        return result;
    }

    private Map<String, Object> createBudget(Map<String, Object> request,
                                             String tenantId,
                                             String actor) {
        long id = store.insert("budget", map(
                "tenant_id", BillingValues.string(request, "tenantId", tenantId),
                "scope_type", BillingValues.requiredString(request, "scopeType").toUpperCase(),
                "scope_id", BillingValues.requiredString(request, "scopeId"),
                "resource_code", BillingValues.string(request, "resourceCode", "").toUpperCase(),
                "period", BillingValues.string(request, "period", BillingValues.month()),
                "budget_amount", BillingValues.positive(request, "budgetAmount"),
                "used_amount", BigDecimal.ZERO,
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "warning_threshold", request.get("warningThreshold") == null
                        ? new BigDecimal("80") : BillingValues.decimal(request.get("warningThreshold")),
                "policy", BillingValues.string(request, "policy", "ALERT").toUpperCase(),
                "status", "ACTIVE",
                "version", 0), actor);
        return require("budget", id, "预算");
    }

    private Map<String, Object> createCostCenter(Map<String, Object> request,
                                                 String tenantId,
                                                 String actor) {
        long id = store.insert("costCenter", map(
                "tenant_id", BillingValues.string(request, "tenantId", tenantId),
                "center_code", BillingValues.requiredString(request, "centerCode").toUpperCase(),
                "center_name", BillingValues.requiredString(request, "centerName"),
                "parent_code", BillingValues.string(request, "parentCode", "").toUpperCase(),
                "owner_id", BillingValues.string(request, "ownerId", ""),
                "status", "ACTIVE"), actor);
        return require("costCenter", id, "成本中心");
    }

    private Map<String, Object> createCostAllocation(Map<String, Object> request, String actor) {
        Long centerId = BillingValues.longValue(request.get("costCenterId"));
        require("costCenter", centerId, "成本中心");
        long id = store.insert("costAllocation", map(
                "cost_center_id", centerId,
                "reference_type", BillingValues.requiredString(request, "referenceType").toUpperCase(),
                "reference_id", BillingValues.requiredString(request, "referenceId"),
                "amount", BillingValues.positive(request, "amount"),
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "occurred_date", BillingValues.string(request, "occurredDate", BillingValues.today())), actor);
        return require("costAllocation", id, "成本归集");
    }

    private Map<String, Object> createApproval(Map<String, Object> request,
                                               String tenantId,
                                               String actor) {
        long id = store.insert("approval", map(
                "approval_no", BillingValues.string(request, "approvalNo", BillingValues.number("AP")),
                "tenant_id", BillingValues.string(request, "tenantId", tenantId),
                "business_type", BillingValues.requiredString(request, "businessType").toUpperCase(),
                "business_id", BillingValues.requiredString(request, "businessId"),
                "amount", BillingValues.decimal(request.get("amount")),
                "status", "APPLIED",
                "current_step", 1,
                "applicant", actor,
                "reviewer", "",
                "reason", BillingValues.requiredString(request, "reason"),
                "decision_comment", ""), actor);
        return require("approval", id, "审批");
    }

    private Map<String, Object> createRevenueShare(Map<String, Object> request, String actor) {
        return createShare(
                BillingValues.requiredString(request, "sourceType").toUpperCase(),
                BillingValues.requiredString(request, "sourceId"),
                BillingValues.requiredString(request, "beneficiaryType").toUpperCase(),
                BillingValues.requiredString(request, "beneficiaryId"),
                BillingValues.positive(request, "grossAmount"),
                BillingValues.decimal(request.get("shareRate")),
                BillingValues.string(request, "currency", "CNY").toUpperCase(),
                actor);
    }

    private Map<String, Object> createPayout(Map<String, Object> request, String actor) {
        long id = store.insert("payout", map(
                "payout_no", BillingValues.string(request, "payoutNo", BillingValues.number("PY")),
                "beneficiary_type", BillingValues.requiredString(request, "beneficiaryType").toUpperCase(),
                "beneficiary_id", BillingValues.requiredString(request, "beneficiaryId"),
                "amount", BillingValues.positive(request, "amount"),
                "currency", BillingValues.string(request, "currency", "CNY").toUpperCase(),
                "status", BillingValues.string(request, "status", "PENDING").toUpperCase(),
                "paid_time", null), actor);
        return require("payout", id, "打款");
    }

    private Map<String, Object> createShare(String sourceType,
                                            String sourceId,
                                            String beneficiaryType,
                                            String beneficiaryId,
                                            BigDecimal gross,
                                            BigDecimal rate,
                                            String currency,
                                            String actor) {
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_REVENUE_SHARE_RATE_INVALID", "分账比例必须在 0 到 1 之间");
        }
        long id = store.insert("revenueShare", map(
                "share_no", BillingValues.number("RS"),
                "source_type", sourceType,
                "source_id", sourceId,
                "beneficiary_type", beneficiaryType,
                "beneficiary_id", beneficiaryId,
                "gross_amount", gross,
                "share_rate", rate,
                "share_amount", gross.multiply(rate).setScale(6, RoundingMode.HALF_UP),
                "currency", currency,
                "status", "PENDING"), actor);
        return require("revenueShare", id, "分账");
    }

    private void createBudgetAlert(Map<String, Object> budget,
                                   String type,
                                   BigDecimal used,
                                   String actor) {
        Long budgetId = BillingValues.rowLong(budget, "id");
        if (store.findOne("budgetAlert", map(
                "budget_id", budgetId,
                "alert_type", type,
                "status", "OPEN")).isPresent()) {
            return;
        }
        store.insert("budgetAlert", map(
                "budget_id", budgetId,
                "alert_type", type,
                "threshold", "EXCEEDED".equals(type) ? new BigDecimal("100")
                        : BillingValues.rowDecimal(budget, "warning_threshold"),
                "used_amount", used,
                "status", "OPEN"), actor);
    }

    private Map<String, Object> currentRate(String from, String to) {
        LocalDateTime now = LocalDateTime.now();
        return store.list("currencyRate", map(
                        "from_currency", from,
                        "to_currency", to,
                        "status", "ACTIVE"), 1, 1000).stream()
                .filter(row -> !dateTime(row.get("effective_time"), now).isAfter(now))
                .findFirst()
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_CURRENCY_RATE_NOT_FOUND", "没有可用汇率: " + from + " → " + to));
    }

    private void requireStatus(String current, List<String> allowed, String label) {
        if (!allowed.contains(current)) {
            throw BillingBusinessException.conflict(
                    "BILLING_STATE_INVALID", label + "当前状态不允许此操作: " + current);
        }
    }

    private void requireTenant(Map<String, Object> row,
                               String tenantId,
                               boolean superAdmin,
                               String label) {
        if (!superAdmin && !tenantId.equals(BillingValues.rowString(row, "tenant_id"))) {
            throw BillingBusinessException.forbidden(
                    "BILLING_ENTERPRISE_TENANT_FORBIDDEN", "无权操作其他租户" + label);
        }
    }

    private Map<String, Object> require(String entity, Long id, String label) {
        if (id == null) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_ID_REQUIRED", label + " ID 不能为空");
        }
        return store.findById(entity, id)
                .orElseThrow(() -> BillingBusinessException.notFound(
                        "BILLING_ENTITY_NOT_FOUND", label + "不存在: " + id));
    }

    private String entity(String module) {
        String entity = MODULE_ENTITIES.get(module);
        if (entity == null) {
            throw BillingBusinessException.notFound(
                    "BILLING_ENTERPRISE_MODULE_NOT_FOUND", "企业模块不存在: " + module);
        }
        return entity;
    }

    private boolean tenantScoped(String entity) {
        return List.of("contract", "organization", "marketplaceOrder", "partnerOrder",
                "budget", "costCenter", "approval").contains(entity);
    }

    private LocalDateTime dateTime(Object value, LocalDateTime defaultValue) {
        if (value == null || BillingValues.string(value).isBlank()) {
            return defaultValue;
        }
        return BillingValues.dateTime(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw BillingBusinessException.unprocessable(
                    "BILLING_JSON_INVALID", "JSON 数据无效");
        }
    }
}
