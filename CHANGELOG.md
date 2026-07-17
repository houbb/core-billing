# CHANGELOG

## [1.0.0] - 2026-07-17

### Phase 1-9：Enterprise Billing Platform

- P1：多余额投影、冻结/确认/释放、乐观并发控制。
- P2：资源、价格规则、价格版本、固定/单位/阶梯计价与费用解释。
- P3：Usage Event 幂等接入、计量器、可计费记录、每日聚合与余额联动。
- P4：额度定义、分配、两阶段预留、BLOCK/OVERAGE/DEGRADE 策略与告警。
- P5：Product、Free/Pro/Enterprise 套餐、版本、试用、升级降级与生命周期。
- P6：Payment Driver SPI、可验签 MOCK 渠道、订单、回调、退款、对账和审计。
- P7：Invoice、Item、Statement、Settlement、Credit Note、税率、PDF/Excel 导出。
- P8：Revenue、Cost、Profit、MRR/ARR、客户/产品分析与线性 Forecast。
- P9：合同、多组织、多币种、营销、Marketplace、Partner、预算、成本中心、审批、分账与 Payout。
- 新增 `/account/billing` 用户商业中心和 `/admin/platform/p1` 至 `/admin/platform/p9` 管理控制台。
- 新增 Flyway V2 迁移、JUnit5 规则测试与 P1-P9 HTTP 端到端测试。

## [0.1.0] - 2026-07-17

### Phase 0：Billing Foundation — 统一商业账本基础设施

建立 core-billing 服务的基石能力：账户管理、交易流水记录、余额实时计算、后台管理控制台。

### 新增

#### 后端核心能力

- **商业账户 (Account)**: 支持个人账户 (PERSONAL) 和企业账户 (ORGANIZATION) 的创建与查询
- **交易流水 (Transaction)**: 核心账本记录，支持充值 (TOP_UP)、消费 (CONSUME)、退款 (REFUND)、手工调整 (ADJUST) 四种交易类型
- **余额计算 (Balance)**: 基于交易流水的实时汇总计算 `SUM(IN) - SUM(OUT)`，使用 `DECIMAL(18,6)` 精度
- **操作审计日志 (Operation Log)**: 所有关键操作自动记录，不可删除
- **余额快照表 (Balance Snapshot)**: 预留 Phase 1 使用的快照表结构

#### 安全设计

- **幂等防护**: `UNIQUE(reference_type, reference_id)` 约束，网络重试不会重复扣费
- **Header 认证**: 通过 `X-User-Id` / `X-Tenant-Id` / `X-Role` Header 传递身份（Phase 0 临时方案）
- **AOP 权限切面**: 基于 `@RequireRole` 注解的三级角色控制（USER / ADMIN / SUPER_ADMIN）
- **手工调整必填原因**: ADMIN/SUPER_ADMIN 进行余额调整时必须填写 reason

#### REST API (共 9 个端点)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/billing/accounts` | 创建账户 |
| GET | `/api/v1/billing/accounts/{id}` | 查询账户详情 |
| GET | `/api/v1/billing/accounts/{id}/balance` | 查询余额 |
| POST | `/api/v1/billing/transactions` | 创建交易（核心） |
| GET | `/api/v1/billing/transactions/{id}` | 交易详情 |
| GET | `/api/v1/billing/transactions/account/{id}` | 账户流水列表 |
| POST | `/api/v1/billing/admin/accounts` | 管理端创建账户 |
| GET | `/api/v1/billing/admin/accounts` | 管理端账户列表 |
| POST | `/api/v1/billing/admin/accounts/{id}/adjust` | 手工调整余额 |

#### Vue3 前端

- **用户端费用中心** (`/account`): 黑金风格余额展示 + 快捷模拟操作（充值/消费/退款） + 交易流水列表
- **管理后台** (`/admin`): 深色侧边栏布局，账户管理（创建/列表/详情）、交易流水审计视图、手工调整弹窗

### 技术栈

- **后端**: Java 17 + Spring Boot 3.3.0 + Spring JDBC + Flyway (迁移)
- **数据库**: SQLite（默认）/ MySQL（生产可选）
- **前端**: Vue 3 + Vite 5 + TypeScript + Pinia + Vue Router + Axios
- **测试**: JUnit 5 + Mockito + Spring Boot Test + TestRestTemplate (端到端)

### 测试覆盖

- **单元测试 15 个**: AccountService (5)、BalanceService (4)、TransactionService (6)
- **集成测试 12 个**: 端到端 REST API 验证完整场景（创建账户 → 充值 → 消费 → 退款 → 手工调整 → 流水查询 → 幂等验证）
- **总计 27 个测试全部通过**

### 文件结构

```
core-billing/
├── pom.xml                    # Maven 配置 (Spring Boot 3.3.0)
├── src/main/ (38 个源文件)
│   ├── api/                   # REST 控制器 + 安全 + 异常处理
│   ├── application/           # 业务服务 + 领域模型 + 接口
│   └── infrastructure/        # JDBC 实现 + 配置
├── web/                       # Vue3 前端 (15 个文件)
├── src/test/ (4 个测试类)
└── src/main/resources/
    ├── application.yml
    ├── application-sqlite.yml
    ├── application-mysql.yml
    └── db/migration/V1__init_billing_tables.sql
```

### 完成标准对照

- [x] ✅ 用户拥有商业账户
- [x] ✅ 可以充值模拟金额
- [x] ✅ 可以消费扣减
- [x] ✅ 所有交易可追溯
- [x] ✅ 支持退款
- [x] ✅ 支持管理员调整
- [x] ✅ 支持未来接支付（不依赖具体支付渠道）
- [x] ✅ 不依赖 Redis/MQ
- [x] ✅ SQLite 可运行

### 明确不做

- ❌ 支付渠道集成（Phase 7）
- ❌ 自动扣款 / 订阅套餐（Phase 6）
- ❌ 发票（Phase 8）
- ❌ 复杂价格计算（Phase 3）
- ❌ 真实 core-identity 集成（Phase 0 用临时 Header）
- ❌ 余额快照实际启用（表已预留，Phase 1 激活）
