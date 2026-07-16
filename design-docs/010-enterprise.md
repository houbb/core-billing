# Phase 9：Enterprise Billing Platform

```text
core-billing-enterprise
```

---

# 一、Phase 9 定位

## 核心目标

打造一个真正的 **Enterprise Billing Platform（企业级商业平台）**。

这里开始，`core-billing` 不再只是：

```text
Billing
```

而是：

```text
Business Platform
```

或者：

```text
Revenue Platform
```

它成为整个 Core Platform 的：

> 商业操作系统（Business Operating System）。

---

到这里：

整个商业闭环已经完成：

```text
Customer

↓

Subscription

↓

Usage

↓

Pricing

↓

Invoice

↓

Payment

↓

Settlement

↓

Revenue

↓

Finance
```

P9：

解决的是：

```text
如何让整个商业体系
能够支撑几十万客户、
多个国家、
多个产品、
多个法人、
多个支付渠道、
多个商业模式。
```

---

# 二、最终目标

不是：

做一个：

```text
支付系统
```

而是：

像：

* Stripe Billing
* Chargebee
* Paddle
* Zuora
* AWS Billing
* Azure Cost Management

这样的：

商业平台。

---

# 三、整体架构

```text
                    Enterprise Billing Platform


+------------+-------------+-------------+-------------+

|            |             |             |             |

↓            ↓             ↓             ↓


Commerce   Revenue      Finance     Analytics


|            |             |             |

+------------+-------------+-------------+

                     |

                     ↓


              Business Runtime


                     |

                     ↓


          AI / Storage / OpenAPI / Workflow
```

Billing：

开始：

成为：

整个平台：

唯一商业中心。

---

# 四、整体能力地图

```text
Enterprise Billing


├── Ledger

├── Balance

├── Pricing

├── Metering

├── Quota

├── Subscription

├── Payment

├── Invoice

├── Revenue

├── Finance

├── Marketplace

├── Contract

├── Tax

├── Multi Currency

├── Multi Organization

├── Partner

├── Promotion

├── Cost Center

├── Budget

├── Approval

└── BI
```

---

# 五、Enterprise Module 1：Contract Runtime

企业：

不是：

点击：

购买。

而是：

签：

合同。

例如：

```text
ABC公司

Enterprise

一年

100万

付款：

Net30
```

建立：

```text
Contract

↓

Subscription

↓

Invoice
```

数据库：

```sql
billing_contract

id

contract_no

customer

start_time

end_time

amount

payment_term

status
```

支持：

```text
合同续签

合同变更

合同审批

电子签署
```

---

# 六、Enterprise Module 2：Multi Organization Runtime

企业：

可能：

有：

```text
美国

中国

日本

欧洲
```

Billing：

必须：

支持：

```text
Organization

↓

Department

↓

Workspace

↓

User
```

例如：

```text
Google

↓

Google Cloud

↓

AI Team

↓

Developer
```

支持：

组织级：

账单。

---

# 七、Enterprise Module 3：Multi Currency Runtime

MVP：

只有：

```text
CNY
```

企业：

支持：

```text
USD

EUR

JPY

HKD

SGD
```

增加：

```sql
billing_currency_rate

id

from_currency

to_currency

rate

effective_time
```

所有：

Invoice：

Payment：

Revenue：

统一：

汇率。

---

# 八、Enterprise Module 4：Promotion Runtime

运营：

必须：

可以：

创建：

```text
优惠券

邀请码

折扣码

首月免费

买一年送一年

满减

限时活动
```

模型：

```text
Promotion

↓

Campaign

↓

Coupon

↓

Discount
```

数据库：

```sql
billing_coupon

billing_campaign

billing_discount
```

---

# 九、Enterprise Module 5：Marketplace Runtime

以后：

插件：

Agent：

Workflow：

全部：

可以：

售卖。

例如：

```text
Marketplace


AI Plugin


Workflow


Knowledge Base


Theme
```

统一：

商业模式：

```text
Listing

↓

Order

↓

Settlement

↓

Revenue Share
```

支持：

平台：

抽成。

例如：

```text
Creator

80%

Platform

20%
```

---

# 十、Enterprise Module 6：Partner Runtime

例如：

代理商：

销售：

```text
Enterprise

License
```

Partner：

获得：

佣金。

支持：

```text
Distributor

Reseller

Affiliate
```

数据库：

```sql
billing_partner

billing_commission

billing_partner_order
```

---

# 十一、Enterprise Module 7：Budget Runtime

很多企业：

最需要：

预算。

例如：

AI：

预算：

```text
¥50000/月
```

达到：

80%

提醒。

100%

停止。

支持：

```text
Department

Workspace

Project
```

预算。

---

# 十二、Enterprise Module 8：Cost Center Runtime

企业：

按：

部门：

统计。

例如：

```text
研发

市场

运营

销售
```

查看：

```text
收入

成本

利润
```

甚至：

AI：

每个：

Agent：

一个：

Cost Center。

---

# 十三、Enterprise Module 9：Approval Runtime

企业：

商业：

不能：

直接：

退款。

例如：

```text
退款

↓

经理审批

↓

财务审批

↓

执行
```

支持：

审批流：

```text
Apply

↓

Review

↓

Approve

↓

Execute
```

接入：

```text
core-workflow
```

---

# 十四、Enterprise Module 10：Revenue Share Runtime

Marketplace：

必须：

支持：

分账。

例如：

```text
用户

支付

100
```

平台：

```text
20
```

作者：

```text
80
```

支持：

```text
Revenue Share

Settlement

Payout
```

未来：

Creator Economy。

---

# 十五、Enterprise Dashboard

首页：

CEO Dashboard：

```text
Revenue

Profit

Cash

MRR

ARR

Growth

Burn Rate

Forecast

Top Customer

Top Product

Top Region

Top Partner
```

支持：

Drill Down。

点击：

MRR：

进入：

客户。

再进入：

Invoice。

再进入：

Payment。

再进入：

Ledger。

形成：

完整追踪。

---

# 十六、管理后台 UX

最终：

菜单：

```text
商业中心

├── 产品(Product)
├── 套餐(Plan)
├── 合同(Contract)
├── 客户(Customer)
├── 订阅(Subscription)
├── 使用量(Metering)
├── 定价(Pricing)
├── 配额(Quota)
├── 支付(Payment)
├── 发票(Invoice)
├── 对账(Settlement)
├── 财务(Finance)
├── 预算(Budget)
├── 成本中心(Cost Center)
├── 优惠(Promotion)
├── 市场(Marketplace)
├── 合作伙伴(Partner)
├── 分账(Revenue Share)
├── KPI
├── BI
```

全部：

统一：

黑金企业风格。

---

# 十七、用户端 UX

企业客户：

首页：

```text
我的企业


Enterprise


合同

有效


订阅

Enterprise


预算

60%


Invoice

2未支付


Payment

正常


组织

12个


Workspace

25个


Agent

210个
```

管理员：

可以：

查看：

整个企业：

商业状态。

---

# 十八、安全设计

企业：

增加：

权限：

```text
FINANCE_ADMIN

PAYMENT_ADMIN

CONTRACT_ADMIN

BILLING_ADMIN

AUDITOR

CFO
```

所有：

商业：

行为：

进入：

```text
core-audit
```

不可：

删除。

支持：

SOX：

审计。

---

# 十九、和其他 Core 模块集成

## core-user

企业组织。

---

## core-auth

商业权限。

---

## core-workflow

审批。

---

## core-notification

账单：

续费：

预算：

通知。

---

## core-storage

Invoice：

合同：

PDF。

---

## core-ai

AI：

成本：

利润：

预测。

---

## core-openapi

API：

商业化。

---

## core-market（未来）

Marketplace：

统一：

收费。

---

# 二十、数据库设计总结

最终：

Billing：

约：

40~60 张核心业务表。

建议按领域拆分：

```text
billing-ledger-*

billing-payment-*

billing-subscription-*

billing-pricing-*

billing-metering-*

billing-finance-*

billing-contract-*

billing-marketplace-*

billing-partner-*
```

避免：

所有：

表：

全部：

混在：

一个：

schema。

---

# 二十一、最终 RoadMap 回顾

| Phase | 模块                          | 核心价值      |
| ----- | --------------------------- | --------- |
| P0    | Billing Foundation          | 统一账本      |
| P1    | Balance                     | 余额账户      |
| P2    | Pricing                     | 定价引擎      |
| P3    | Metering                    | 使用量统计     |
| P4    | Quota                       | 配额控制      |
| P5    | Subscription                | 套餐与订阅     |
| P6    | Payment                     | 支付运行时     |
| P7    | Invoice                     | 发票与结算     |
| P8    | Revenue & Finance           | 收入运营与财务分析 |
| P9    | Enterprise Billing Platform | 企业商业平台    |

---

# 二十二、与业界产品的能力对标

| 能力             | Stripe Billing | Chargebee | AWS Billing | `core-billing` P9 |
| -------------- | -------------- | --------- | ----------- | ----------------- |
| 账本             | ✅              | ✅         | ✅           | ✅                 |
| 余额             | ✅              | ✅         | ✅           | ✅                 |
| 按量计费           | ✅              | ✅         | ✅           | ✅                 |
| 配额             | ⚠️             | ⚠️        | ✅           | ✅                 |
| 订阅             | ✅              | ✅         | ✅           | ✅                 |
| 支付             | ✅              | ❌（依赖支付）   | ❌           | ✅                 |
| 发票             | ✅              | ✅         | ✅           | ✅                 |
| 财务分析           | ⚠️             | ⚠️        | ✅           | ✅                 |
| 合同管理           | ⚠️             | ✅         | ✅           | ✅                 |
| Marketplace 分账 | ❌              | ❌         | ⚠️          | ✅                 |
| AI 成本中心        | ❌              | ❌         | ⚠️          | ✅                 |
| 多组织、多租户        | ⚠️             | ✅         | ✅           | ✅                 |

---

# 二十三、最终完成标准

完成 P9 后，`core-billing` 将具备：

✅ 完整的商业生命周期管理（Lead → Contract → Subscription → Usage → Billing → Payment → Renewal）
✅ SaaS、AI、API、Marketplace 四种商业模式统一支撑
✅ 多组织、多产品、多币种、多支付渠道架构
✅ Revenue、Profit、Cost、Budget 全链路分析
✅ Marketplace 与 Revenue Share 能力
✅ 企业合同、审批、预算、成本中心支持
✅ 与 `core-user`、`core-auth`、`core-workflow`、`core-notification`、`core-ai`、`core-openapi` 等所有 Core 模块形成统一商业中台。

---

# 最终形态

整个 Core Platform 将形成四层商业架构：

```text
                Business Layer
        (Marketplace / Contract / Partner)

                       │

                Commerce Layer
 (Subscription / Pricing / Payment / Invoice)

                       │

              Finance Layer
 (Ledger / Balance / Revenue / Budget / Cost)

                       │

              Runtime Layer
(Metering / Quota / Settlement / Analytics)
```

到此，`core-billing` 已经从一个简单的支付模块，演进为一个能够支撑 **AI 平台、SaaS 平台、开放平台、插件市场以及企业级软件** 的 **Enterprise Billing Platform（企业商业平台）**。这一层也是整个 Core Platform 最重要的商业中枢。
