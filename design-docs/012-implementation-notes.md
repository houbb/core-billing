# Implementation Notes

## Metadata

- **Task / Feature:** P1-P9 Billing Platform
- **Date started:** 2026-07-17
- **Implementation owner:** Codex
- **Related Unknowns Report:** `design-docs/011-implementation-unknowns.md`

## Confirmed Discoveries

### Discovery D-001

- **What was discovered:** 当前运行环境和 Maven 工程固定在 Java 17。
- **Evidence:** `java -version` 与 `pom.xml`。
- **Why it matters:** 直接升级 Java 21 会导致本次无法真实验证。
- **Affected scope:** 所有新增 Java 代码。
- **Action taken:** 使用 Java 17 支持的 record/switch 等语法，不改运行基线。

### Discovery D-002

- **What was discovered:** P0 已有 SQLite 数据文件，不能通过重建 schema 升级。
- **Evidence:** `data/core-billing.db`。
- **Why it matters:** 必须保留账户和交易历史。
- **Affected scope:** 数据库。
- **Action taken:** 只追加 Flyway V2+ migration。

## Decisions

### Decision DEC-001

- **Decision:** API 继续使用 `/api/v1/billing`。
- **Alternatives considered:** 严格照文档示例使用 `/api/billing`。
- **Reason:** 保持 P0 兼容。
- **Evidence:** 现有 Controller 与前端 API。
- **Owner / approver:** Repository convention。
- **Reversibility:** 可通过 alias Controller 兼容，但当前不需要。

### Decision DEC-002

- **Decision:** P6 使用可插拔 MOCK Payment Driver 和 HMAC 回调。
- **Alternatives considered:** 接 Stripe、只做无回调 CRUD。
- **Reason:** 没有真实凭证；MOCK 仍能验证订单、回调、余额、退款和对账完整语义。
- **Evidence:** P6 设计要求 Driver SPI 和“结果以回调为准”。
- **Owner / approver:** Architecture。
- **Reversibility:** 高，新增 Driver 即可替换。

## Assumptions

### Assumption A-001

- **Assumption:** 默认币种为 CNY。
- **Why it is currently acceptable:** P0 配置和 P9 MVP 描述一致。
- **Risk:** 国际客户显示不完整。
- **How it will be validated:** 多币种转换测试。
- **Reversal plan:** 使用 P9 currency rate 与各业务表 currency 字段。

## Unresolved Risks

| Risk | Impact | Current mitigation | Owner | Review trigger |
|---|---:|---|---|---|
| 真实支付渠道未接入 | 5 | PaymentDriver SPI + MOCK contract | Future payment owner | 选择首个商户时 |
| 外部 Core 未联调 | 4 | external ID + 本地状态机 | Platform integration owner | 对接 core-workflow/user 时 |

## Tests Added or Updated

| Test | Purpose | Result |
|---|---|---|
| `PricingRuntimeServiceTest` | 单位价格、渐进阶梯价格 | Pass |
| `QuotaRuntimeServiceTest` | BLOCK 策略防止额度超卖 | Pass |
| `MockPaymentDriverTest` | HMAC 回调验签与篡改拒绝 | Pass |
| `BillingPlatformEndToEndTest` | P1-P9 主链路、幂等、租户越权、金额篡改、PDF | Pass |
| 原 P0 单元与集成测试 | 账户、账本、余额与管理员操作回归 | Pass |

## Confirmed Discoveries

### Discovery D-003

- **What was discovered:** SQLite 的 DATETIME 在不同写入方式下可能返回 ISO 文本、`Timestamp` 或 epoch 毫秒。
- **Evidence:** P8 第一次端到端运行中 `paid_time` 返回 epoch。
- **Why it matters:** P2/P3/P6/P7/P8/P9 均有生效时间或状态时间。
- **Affected scope:** 所有时间比较。
- **Action taken:** 统一 `BillingValues.dateTime` 兼容三种表示，并回归通过。

### Discovery D-004

- **What was discovered:** Gross Revenue 不能只统计当前状态为 SUCCESS 的订单，否则 REFUNDED 订单会只进入退款、不进入毛收入。
- **Evidence:** 财务端到端快照。
- **Why it matters:** 会错误产生负净收入。
- **Affected scope:** P8 Revenue。
- **Action taken:** SUCCESS 和 REFUNDED 均计入 Gross，Refund 单独扣减。

## Deviations

### Deviation DEV-001

- **Original plan:** P6 示例接入 Stripe。
- **Actual implementation:** 可验签 MOCK Payment Driver。
- **Reason for deviation:** 仓库没有商户选择、凭证或外部网络契约。
- **User-visible effect:** 可完整演示订单、回调、充值、退款、对账，但不会真实扣款。
- **Data / API effect:** Payment SPI 和数据模型与真实渠道一致。
- **Risk introduced:** 上线前必须实现真实 Driver。
- **Approval required:** No
- **Follow-up:** 选择首个支付渠道后添加 contract test。

## Verification Evidence

- `mvn clean test`：33 tests，0 failure，0 error。
- `npm exec vue-tsc -- --noEmit`：通过。
- `npm run build`：114 modules transformed，生产构建通过。
- `mvn clean package -DskipTests`：生成 `target/core-billing-1.0.0.jar`。
- `npm audit --omit=dev`：0 vulnerabilities。
- DB 规则扫描：V2 新增 55 张表，全部具备审计五字段。

## Rollback Notes

- Code rollback: 回退新增 Controller/Service/Repository 和前端路由。
- Data rollback: 新表只被新功能使用；回滚代码后保留表，避免丢失商业数据。
- Configuration rollback: 删除新增 `core.billing.*` 配置即可恢复默认。
- External-system rollback: 当前没有真实外部写操作。
- Recovery validation: P0 原有测试必须继续通过。
