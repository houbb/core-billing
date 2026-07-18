# Unknowns Report

## Metadata

- **Task / Feature:** 实现 `design-docs` 中 P1-P9 的全部阶段能力
- **Mode:** Standard
- **Date:** 2026-07-17
- **Prepared by:** Codex
- **Scope:** Spring Boot 后端、SQLite/MySQL 迁移、Vue3 管理端与用户端、JUnit5 单元测试和 HTTP 端到端测试

## Intent

### User-visible problem

仓库当前只具备 P0 商业账户与不可变账本，余额冻结、定价、计量、配额、订阅、支付、账单、财务分析和企业商业能力仍停留在设计文档。

### Desired behavior change

在同一个独立 `core-billing` 服务中形成 P1-P9 的可运行闭环：

`Balance → Pricing → Metering → Quota → Subscription → Payment → Invoice → Finance → Enterprise`

### Affected users and workflows

- 用户：查看余额、用量、额度、套餐、订阅、支付和账单。
- 管理员：维护价格、计量器、配额、套餐、支付、账单、财务指标和企业配置。
- 业务服务：上报 usage、预留余额/额度、计算价格、触发支付回调。

### Success criteria

- P1-P9 每阶段至少有可调用 API、持久化模型和前端入口。
- 关键跨阶段流程真实联动，不是孤立 CRUD。
- 所有新增表符合项目 DB 强制字段、注释、索引、无外键要求。
- JUnit5 断言覆盖核心规则，HTTP 端到端覆盖 P1-P9 主链路。
- Maven 测试、后端构建、前端类型检查、前端构建全部通过。

### Non-goals

- 不接入真实 Stripe/PayPal/微信/支付宝商户。
- 不引入 Kafka、Flink、Redis、MQ、数据仓库或外部工作流服务。
- 不实现文档明确列入“暂不实现”的 AI 调价、复杂税务、自动扣款、风控、OLAP 等增强项。

## Evidence Reviewed

| Source | Location | What it confirms | Confidence |
|---|---|---|---|
| Code | `src/main/java` | 当前只有 P0 账户、账本、简化余额查询与 Header 角色校验 | High |
| Tests | `src/test/java` | 当前端到端只覆盖账户、充值、消费、退款、调整 | High |
| Schema | `V1__init_billing_tables.sql` | 只有 P0 四张表，P1 快照表仅预留 | High |
| Documentation | `design-docs/002-010` | P1-P9 的核心模型、MVP 范围与完成标准 | High |
| Design reference | `AGENTS.md`、现有 Vue CSS | Apple 风格、100% 宽度、三级按钮、UTF-8 | High |
| Historical decision | Git `86af3b7` | P0 是单次初始实现，没有后续阶段历史包袱 | High |

## Confirmed Facts

| Fact | Evidence | Relevance |
|---|---|---|
| 运行环境只有 Java 17 | `java -version`、`pom.xml` | 不能直接按技术文档切 Java 21，否则本机无法验证 |
| 现有 API 前缀是 `/api/v1/billing` | 三个现有 Controller | 新 API 必须保持兼容 |
| 默认数据库已有真实 SQLite 文件 | `data/core-billing.db` | 必须通过追加 Flyway migration 升级，不能重建 |
| 没有真实支付凭证和外部 Core 配置 | 配置与仓库扫描 | 支付和跨 Core 联动必须使用本地可替换实现 |
| 前端没有 i18n 基础设施 | `web/package.json`、`web/src` | 新增轻量字典而不引入额外依赖 |

## Critical Unknowns

| Unknown | Category | Evidence / Reasoning | Impact | Probability | Irreversibility | Late discovery cost | Priority | Disposition | Resolution |
|---|---|---|---:|---:|---:|---:|---:|---|---|
| 文档示例路径没有 `/v1`，现有代码有 | Known unknown | P1-P9 文档均写 `/api/billing`，P0 实现写 `/api/v1/billing` | 4 | 5 | 2 | 4 | 160 | Decision | 以现有公开 API 为准，统一 `/api/v1/billing` |
| 支付渠道未指定且无凭证 | Known unknown | P6 只建议“例如 Stripe” | 5 | 5 | 2 | 4 | 200 | Experiment | 实现 `PaymentDriver` SPI 与可验签 MOCK Driver，真实渠道可后装 |
| P9 依赖其他 Core，但仓库内不存在 | Unknown unknown candidate | `core-workflow`、`core-user` 等均不在当前仓库 | 5 | 5 | 2 | 4 | 200 | Monitor | 本地状态机 + external reference 字段，不伪造跨服务调用 |
| P0 ledger 与 P1 materialized balance 如何一致 | Known unknown | 当前余额来自 SUM(ledger)，P1 要可用/冻结余额和乐观锁 | 5 | 5 | 4 | 5 | 500 | Blocker | 余额表作为可并发投影，所有新交易同步投影；冻结确认写 ledger 时禁止二次扣减 |
| P7 PDF 无现有库 | Known unknown | `pom.xml` 没有 PDF 依赖 | 3 | 5 | 1 | 2 | 30 | Decision | 用 JDK 生成最小标准 PDF，避免新增依赖和版本风险 |
| DB 字段规范与技术文档示例不一致 | Unknown known | AGENTS 强制每表五个审计字段，设计稿只列业务字段 | 4 | 5 | 3 | 5 | 300 | Decision | 强制遵循 AGENTS，所有新表补齐字段、注释和索引 |

## Implicit Expectations

| Expectation | Why it may exist | How to surface it |
|---|---|---|
| 每阶段不是孤立 CRUD | 用户要求“实现所有 P”，项目要求尽可能与已有模块打通 | 端到端主链路测试 |
| 旧 P0 API 不能失效 | 用户明确 P0 已实现过 | 回归运行原有测试 |
| 管理端能快速看到所有阶段 | 每份设计都有 Admin UX | 统一阶段控制台与导航 |
| 关键商业记录不可物理删除 | Ledger、Usage、Invoice、Payment 文档反复强调不可删除 | 不提供 DELETE API，使用状态流转 |

## Blind-Spot Candidates

| Candidate | Why it may matter | Validation method |
|---|---|---|
| SQLite 并发更新行数为 0 | 余额/配额可能并发超卖 | 乐观更新断言测试 |
| 重复 usage/callback/invoice | 会造成重复扣费或重复账单 | 唯一索引 + 端到端幂等测试 |
| 冻结金额与 ledger 双扣 | P1 最危险跨模型问题 | 单元测试实际金额变化 |
| 负数或精度异常 | 商业金额不能静默错误 | 统一 `BigDecimal` 和正数校验 |
| 多租户查询越界 | P8/P9 聚合容易泄露其他租户 | 默认从 SecurityContext 限定 tenant，管理接口显式授权 |

## Decisions Required

| Decision | Options | Trade-offs | Recommended owner | Deadline / Trigger |
|---|---|---|---|---|
| Java 版本 | 17 / 21 | 21 符合文档但当前环境不可验证 | Architecture | 已决定保留 17 |
| 支付实现 | 真实 Stripe / MOCK SPI | MOCK 可完整测试但不能真实收款 | Architecture | 已决定 MOCK SPI |
| 前端页面数量 | 每功能独立页 / 阶段控制台 | 控制台代码更少且便于连续交付 | Product | 已决定阶段控制台 |

## Experiments or Prototypes Required

| Question | Method | Success signal | Cost | Owner |
|---|---|---|---|---|
| SQLite 乐观锁能否防止超卖 | JUnit5 repository integration test | 余额不足时更新 0 行并抛业务异常 | Low | Codex |
| PDF 是否可下载 | HTTP 端到端测试 | `application/pdf` 且内容以 `%PDF` 开头 | Low | Codex |
| P1-P9 是否形成闭环 | 顺序 HTTP 测试 | 使用量产生费用、额度变化、账单和财务汇总可追溯 | Medium | Codex |

## Safe Assumptions

| Assumption | Why it is safe | Reversal plan |
|---|---|---|
| 默认币种 CNY | P0 配置和 P9 文档 MVP 均如此 | 通过 currency/rate 表扩展 |
| MOCK 支付成功由签名回调触发 | 与真实 provider 的 webhook 语义一致 | 替换 SPI Driver |
| 新前端默认中文，预留英文 | 当前产品全部中文 | 切换字典或接入正式 i18n 包 |
| P9 外部主体只保存 external ID | 服务不能访问其他 Core 数据库 | 后续通过 HTTP Client 补充校验 |

## Deferred Unknowns

| Unknown | Why deferred | Monitoring / Follow-up |
|---|---|---|
| 真实支付渠道字段和签名算法 | 没有商户与渠道选择 | 新增 Driver 时编写渠道契约测试 |
| 国家税务和电子发票格式 | 文档明确暂不实现 | P7 Tax SPI 扩展 |
| 集团级复杂组织继承 | 文档明确暂不实现 | P9 organization path 字段预留 |

## Recommended Implementation Boundary

### Implement now

- P1-P9 文档 MVP 范围。
- P9 十个企业模块的本地可运行核心状态与 API。
- 跨阶段关键联动、管理/用户前端入口、测试和迁移。

### Do not implement now

- 设计文档明确列为暂不实现的外部或大规模能力。
- 真实第三方资金操作与外部 Core 数据写入。

### Interfaces or data contracts to freeze

- `/api/v1/billing` 前缀。
- usage `eventId`、payment `idempotencyKey/callbackId`、invoice `tenantId+period` 幂等键。
- 金额统一 `DECIMAL(18,6)` / `BigDecimal`。

### Areas that must remain reversible

- Payment Driver。
- P9 外部 Core 集成。
- 前端 i18n 实现。

## Verification Plan

### Automated

- Unit tests: 价格算法、余额冻结/确认/释放、配额两阶段、签名。
- Integration tests: Flyway、repository 并发条件。
- Contract tests: P1-P9 HTTP 端点、幂等、错误状态。
- Static analysis: Java 编译、Vue TypeScript。

### Manual

- Happy path: 完整商业闭环。
- Empty state: 新租户 dashboard。
- Failure path: 余额/配额不足、错误签名。
- Recovery path: release、refund、credit note。
- Permission boundaries: USER 与 ADMIN/SUPER_ADMIN。
- Mobile / responsive: 100% 宽度、自适应卡片。
- Accessibility: 表单 label、按钮文本、状态颜色同时有文字。
- Performance: 分页与必要索引。

### Observability

- Logs: 余额、usage、payment callback、invoice、approval 状态变化。
- Metrics: P8 dashboard 从事实表生成。
- Alerts: quota/budget 阈值表。
- Audit trail: 所有新表审计字段，关键操作写 operation log。

## Handoff

- [x] Acceptance criteria
- [x] Explicit invariants
- [x] Data and interface contracts
- [x] Test cases
- [x] Rollback requirements
- [x] Observability requirements
- [x] Non-goals
- [x] Implementation notes file

