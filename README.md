# Core Billing

统一商业账本基础设施 — 企业级计费平台。

## 快速体验

### 环境要求

- **后端**: Java 17 + Maven 3.8+
- **前端**: Node.js 18+
- **数据库**: SQLite（默认零配置）/ MySQL（生产可选）

### 1. 启动后端

```bash
cd core-billing-backend
mvn spring-boot:run
```

服务启动在 `http://localhost:8102`，数据库自动创建在 `core-billing-backend/data/` 下。

### 2. 启动前端

```bash
cd core-billing-frontend
npm install
npm run dev
```

开发服务器启动在 `http://localhost:5173`，API 请求自动代理到后端。

### 3. 打开浏览器

- **用户端费用中心**: `http://localhost:5173/account` — 余额查询、充值消费、交易流水
- **管理后台**: `http://localhost:5173/admin` — 账户管理、交易审计、手工调整

### 4. 一键构建部署

前端构建产物自动输出到后端 `static/` 目录，打成一个 JAR 包即可：

```bash
cd core-billing-frontend && npm run build
cd ../core-billing-backend && mvn package -DskipTests
java -jar target/core-billing-1.0.0.jar
```

然后直接访问 `http://localhost:8102` 即可。

## 项目结构

```
core-billing/
├── core-billing-backend/     # Spring Boot 后端
│   ├── pom.xml
│   ├── src/main/java/        # 三层架构：api / application / infrastructure
│   ├── src/main/resources/   # 配置 + Flyway 迁移脚本
│   └── src/test/             # JUnit5 测试
├── core-billing-frontend/    # Vue 3 前端
│   ├── src/pages/            # 页面组件
│   ├── src/api/              # API 层
│   └── vite.config.ts
├── design-docs/              # 设计文档（Phase 0-9）
└── CHANGELOG.md
```

## 功能总览

| Phase | 模块 | 说明 |
|-------|------|------|
| P0 | Foundation | 商业账本基础设施：账户、交易流水、余额计算 |
| P1 | Balance | 多余额投影、冻结/确认/释放、乐观并发控制 |
| P2 | Pricing | 固定/单位/阶梯计价、价格版本、费用解释 |
| P3 | Metering | Usage Event 幂等接入、计量器、每日聚合 |
| P4 | Quota | 额度定义与分配、两阶段预留、BLOCK/OVERAGE/DEGRADE 策略 |
| P5 | Subscription | Product、Free/Pro/Enterprise 套餐、试用、升降级 |
| P6 | Payment | Payment Driver SPI、MOCK 渠道、订单、回调、退款、对账 |
| P7 | Invoice | 发票、账单、结算、Credit Note、税率、PDF/Excel 导出 |
| P8 | Finance | Revenue/Cost/Profit、MRR/ARR、客户/产品分析、Forecast |
| P9 | Enterprise | 合同、多组织、多币种、营销、预算、成本中心、审批、分账 |

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Java 17 + Spring Boot 3.3.0 |
| 数据库访问 | Spring JDBC + Flyway |
| 数据库 | SQLite（默认）/ MySQL（生产） |
| 前端框架 | Vue 3 + Vite 5 + TypeScript |
| 状态管理 | Pinia |
| 路由 | Vue Router 4 |
| HTTP 客户端 | Axios |
| 测试 | JUnit 5 + Spring Boot Test |

## API 概览

所有接口前缀：`/api/v1/billing`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/accounts` | 创建账户 |
| GET | `/accounts/{id}` | 查询账户详情 |
| GET | `/accounts/{id}/balance` | 查询余额 |
| POST | `/transactions` | 创建交易（核心） |
| GET | `/transactions/account/{id}` | 账户流水列表 |
| POST | `/admin/accounts` | 管理端创建账户 |
| GET | `/admin/accounts` | 管理端账户列表 |
| POST | `/admin/accounts/{id}/adjust` | 手工调整余额 |

> 完整 P1-P9 管理控制台接口见 `/admin/platform/p1` ~ `/admin/platform/p9`。

## 安全模型

- **Header 认证**: `X-User-Id` / `X-Tenant-Id` / `X-Role`（Phase 0 临时方案）
- **三级角色**: USER / ADMIN / SUPER_ADMIN
- **AOP 权限切面**: 基于 `@RequireRole` 注解
- **幂等防护**: `UNIQUE(reference_type, reference_id)` 约束

## License

MIT © 2026 houbb