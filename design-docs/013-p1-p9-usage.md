# P1-P9 使用说明

## 启动

```powershell
mvn spring-boot:run
```

默认地址：

- 用户端：`http://localhost:8102/#/account/billing`
- 管理端：`http://localhost:8102/#/admin/platform/p1`
- API：`http://localhost:8102/api/v1/billing`

本地请求身份：

```text
X-User-Id: demo-user
X-Tenant-Id: default
X-Role: SUPER_ADMIN
```

## 核心闭环

1. `POST /admin/accounts` 创建账户。
2. `POST /accounts/{id}/deposit` 充值。
3. `POST /pricing/calculate` 计算资源价格。
4. `POST /usage/events` 上报用量并按需扣费。
5. `POST /quota/reserve`、`/quota/commit` 控制额度。
6. `POST /subscriptions` 订阅 Free/Pro/Enterprise 套餐。
7. `POST /payments/orders` 创建订单；本地通过
   `POST /admin/payments/orders/{orderNo}/mock-complete` 模拟验签成功回调。
8. `POST /admin/invoices/generate` 生成账单，用户可下载 PDF/Excel。
9. `POST /admin/finance/snapshots` 生成收入、利润、KPI 和预测。
10. `/admin/enterprise/*` 管理合同、多组织、汇率、营销、市场、伙伴、预算、成本中心、审批和分账。

## MOCK 支付

MOCK Driver 保留真实渠道的订单、回调、验签、退款和对账语义，但不会发生真实资金操作。

生产接入真实渠道时，实现 `PaymentDriver` 并新增 `billing_payment_channel` 配置；回调密钥通过：

```text
BILLING_PAYMENT_CALLBACK_SECRET
```

覆盖，禁止使用默认值。

## 数据升级

Flyway `V2__billing_platform_p1_p9.sql` 会在保留 P0 账户和流水的前提下补齐 P1-P9 表结构。所有新表：

- 包含 `id/create_time/update_time/create_user/update_user`
- 有业务注释和必要索引
- 不使用数据库外键

