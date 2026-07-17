# Post-Implementation Review

## Metadata

- **Task / Feature:** P1-P9 Enterprise Billing Platform
- **Date completed:** 2026-07-17
- **Reviewer:** Codex（三轮）
- **Related Unknowns Report:** `design-docs/011-implementation-unknowns.md`
- **Related implementation notes:** `design-docs/012-implementation-notes.md`

## Behavior Changes

### Before

- 只有账户、不可变交易流水和基于 ledger SUM 的简化余额。
- 没有价格、usage、配额、订阅、支付、账单、财务或企业运行时。

### After

- P1-P9 均有持久化、业务状态机、REST API、用户/管理前端入口和测试。
- Usage 可计价并联动余额；套餐可分配配额；支付可联动充值/订阅/账单结算。
- Invoice 可导出 PDF/Excel；Finance 可生成 Revenue/Profit/KPI/Forecast。
- P9 覆盖合同、多组织、多币种、营销、Marketplace、Partner、预算、成本中心、审批、分账和 Payout。

## Files and Systems Affected

| Area | Change | Why it changed |
|---|---|---|
| Flyway | 新增 V2、55 张 P1-P9 表、默认资源/价格/套餐 | 支撑完整商业模型 |
| Application | 9 个 Runtime Service + 通用持久化端口 | 保持三层架构与最少重复 |
| API | 新增 P1-P9 Controller | 提供用户、管理和服务集成入口 |
| Payment | Driver SPI + HMAC MOCK Driver | 无真实凭证时保持可测试、可替换 |
| Vue | 用户商业中心、P1-P9 管理控制台、i18n 字典 | 完成前端与路由注册 |
| Tests | 规则单测、P0 回归、P1-P9 E2E | 锁定金额、幂等、状态机和租户隔离 |

## Assumptions Review

| Assumption | Status | Evidence | Action |
|---|---|---|---|
| 默认 CNY | Confirmed | P0 配置、P9 汇率扩展、E2E | Keep |
| MOCK 可表达真实 webhook 语义 | Confirmed | 验签、幂等、订单联动测试 | Keep，真实 Driver 后替换 |
| Java 17 保持可验证 | Confirmed | clean compile/package | Keep |
| 外部 Core 使用 external ID | Still unknown | 当前仓库无其他 Core | Monitor |

## Unknowns Review

### Resolved

| Unknown | Resolution | Evidence |
|---|---|---|
| API 版本前缀 | 统一 `/api/v1/billing` | 前后端与 E2E |
| ledger 与冻结余额双扣 | 冻结确认写 ledger 时关闭二次投影 | P1 E2E 100→92 |
| SQLite 时间表示 | 统一三种时间解析 | P8/P9 E2E |
| 退款财务口径 | Gross 包含后续退款订单 | Finance E2E |
| 租户按 ID 越权 | 账户、流水、预留、用量、配额、支付、账单、企业动作增加归属校验 | 403 E2E |

### Remaining

| Unknown | Risk | Follow-up |
|---|---|---|
| 首个真实支付渠道 | 无法真实收款 | 实现 PaymentDriver + 渠道 contract test |
| core-identity JWT/JWKS | 当前仍是 Phase 0 Header 身份 | 接入平台统一认证 |
| 外部 workflow/notification | 审批和告警只在本地落库 | 增加 HTTP/Outbox Adapter |
| MySQL migration 方言 | V1/V2 当前以默认 SQLite 为实测基线 | 上生产 MySQL 前补 vendor migration test |

## Deviations

| Deviation | Reason | User-visible effect | Risk | Approved |
|---|---|---|---|---|
| Stripe 改为 MOCK Driver | 无商户凭证 | 不发生真实资金操作 | 上线前需接真实渠道 | Yes |
| P9 外部集成改为本地状态机 | 其他 Core 不在仓库 | 功能可运行但无外部消息 | 后续联调 | Yes |
| Excel 使用 SpreadsheetML `.xls` | 避免新增依赖 | Excel 可直接打开 | 不支持 `.xlsx` 高级格式 | Yes |

## Verification Evidence

### Automated checks

- [x] Unit tests
- [x] Integration tests
- [x] Migration tests
- [x] Contract / HTTP E2E tests
- [x] Build
- [x] Type check
- [x] Production dependency audit

### Manual/static checks

- [x] Happy path
- [x] Failure and recovery path
- [x] Permission boundaries
- [x] Responsive layout rules
- [x] DB audit fields and indexes
- [x] Three code review passes

## Rollback and Recovery

- **Rollback trigger:** 金额投影不一致、重复扣费、支付回调错误或迁移失败。
- **Code rollback steps:** 回退 P1-P9 Controller/Service/Store 与前端路由。
- **Data rollback steps:** 不删除新增商业表；停用新入口后保留数据供核对。
- **Configuration rollback steps:** 停止定时任务或切换支付 Driver 配置。
- **Recovery verification:** ledger 与 balance 对账、payment/invoice/settlement 关联核对、重新运行 E2E。

## Maintainer Notes

- `billing_transaction` 仍是资金事实；`billing_balance` 是支持冻结和并发控制的投影。
- 余额或配额 reserve 后，必须 commit 或 release。
- Invoice、Usage、Payment callback、Ledger 不提供物理删除。
- 新增支付渠道只实现 `PaymentDriver`，不要把 SDK 调用写进 Application Service。
- 任何租户级按 ID 查询都必须经过归属校验。

## Understanding Check

1. 为什么冻结确认写 ledger 时不能再次更新余额投影？
2. 为什么 Gross Revenue 必须包含后来变为 REFUNDED 的订单？
3. usage、payment callback 和 invoice 分别使用什么幂等键？
4. 真实支付 Driver 失败后，哪些本地状态允许安全重试？
5. 普通 ADMIN 与 SUPER_ADMIN 的跨租户边界在哪里？
6. SQLite 时间值可能以哪三种类型返回？
7. 如果账单金额与支付订单金额不一致，系统在哪一层拒绝？

