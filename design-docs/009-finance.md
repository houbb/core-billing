# Phase 8：Revenue & Finance Runtime 详细设计

```text
core-billing-finance
```

---

# 一、Phase 8 定位

## 核心目标

建立**统一收入运营（Revenue Operations）与财务分析（Finance Analytics）平台**。

到了这里，`core-billing` 不再只是：

> 能收费。

而是开始回答企业真正关心的问题：

```text
今天赚了多少钱？

哪些产品赚钱？

哪些客户亏钱？

MRR是多少？

ARR是多少？

续费率是多少？

LTV是多少？

CAC是多少？

现金流怎么样？
```

这也是 SaaS 公司 CEO、CFO、运营负责人每天查看的数据。

---

## 与前面阶段的关系

| Phase  | 能力                |
| ------ | ----------------- |
| P0     | Ledger（流水）        |
| P1     | Balance（余额）       |
| P2     | Pricing（定价）       |
| P3     | Metering（计量）      |
| P4     | Quota（额度）         |
| P5     | Subscription（订阅）  |
| P6     | Payment（支付）       |
| P7     | Invoice（账单）       |
| **P8** | **Revenue（收入运营）** |

一句话：

> Billing 到这里，终于变成企业经营系统。

---

# 二、为什么 Revenue Runtime 要独立？

很多平台：

只有：

```text
订单

支付

结束
```

真正企业：

每天都会问：

```text
收入是多少？

增长多少？

利润是多少？

哪个套餐卖得最好？

哪个国家收入最高？

哪个AI模型成本最高？

```

这些：

不能实时 SQL 查询。

必须：

建立：

```text
Finance Runtime
```

---

# 三、整体架构

```text
                    core-billing


                         |

                         ↓


             Revenue & Finance Runtime


     +----------+----------+----------+

     |          |          |          |

     ↓          ↓          ↓          ↓


 Revenue     Cost      KPI      Forecast


                         |

                         ↓


                 Analytics Dashboard
```

---

# 四、核心设计原则

## 原则1：运营数据来自业务事实

Revenue：

不是：

```text
人工填写
```

而是：

来自：

```text
Payment

Invoice

Subscription

Ledger
```

统一计算。

---

## 原则2：所有指标可追溯

例如：

MRR：

点击：

应该：

能够看到：

```text
MRR

↓

哪些客户

↓

哪些订阅

↓

哪些Invoice

↓

哪些Payment
```

不能：

只是：

```text
MRR = 12000
```

不知道哪里来的。

---

## 原则3：运营指标快照

例如：

昨天：

```text
MRR

10000
```

今天：

```text
12000
```

不能：

重新计算昨天。

所以：

每天：

保存：

```text
Finance Snapshot
```

---

# 五、核心模块设计

```text
core-billing-finance

├── Revenue Runtime
├── Cost Runtime
├── Profit Runtime
├── KPI Runtime
├── Forecast Runtime
├── Customer Analytics
├── Product Analytics
├── Finance Dashboard
└── Report Center
```

---

# 六、Revenue Runtime

负责：

收入统计。

数据库：

```sql
billing_revenue_snapshot

id

snapshot_date

currency

gross_revenue

net_revenue

refund_amount

created_time
```

---

指标：

```text
Gross Revenue

Net Revenue

Refund

Outstanding

```

例如：

```text
今日收入

¥12,300

退款

¥300

净收入

¥12,000
```

---

# 七、Cost Runtime

收入：

不是利润。

例如：

AI：

用户：

支付：

```text
¥100
```

调用：

OpenAI：

成本：

```text
¥35
```

利润：

```text
65
```

所以：

建立：

```text
Cost Runtime
```

数据库：

```sql
billing_cost_record

id

resource_code

provider

cost

created_time
```

支持：

```text
OpenAI

Claude

Gemini

阿里百炼

Azure OpenAI
```

---

# 八、Profit Runtime

利润：

计算：

```text
Revenue

-

Cost

=

Profit
```

展示：

```text
收入

100000


成本

35000


利润

65000
```

支持：

```text
Gross Margin

Net Margin
```

---

# 九、KPI Runtime

企业：

最重要模块。

支持：

## 收入指标

```text
MRR

ARR

Revenue

Growth
```

---

## 用户指标

```text
Active Customer

Paying Customer

Trial Customer

Enterprise Customer
```

---

## 商业指标

```text
ARPU

ARPPU

LTV

CAC

Churn

Renew Rate
```

---

数据库：

```sql
billing_kpi_snapshot

id

snapshot_date

kpi_code

value
```

---

# 十、Forecast Runtime

预测：

未来收入。

例如：

根据：

```text
Subscription

Renewal

Growth
```

预测：

```text
下个月收入

¥120000
```

MVP：

采用：

简单：

线性预测。

企业版：

接入：

AI。

---

# 十一、Customer Analytics

分析：

客户。

例如：

ABC公司：

```text
MRR

¥10000

续费率

98%

利润

80%

```

支持：

RFM分析。

例如：

```text
高价值

高风险

沉默客户

流失客户
```

---

# 十二、Product Analytics

分析：

产品。

例如：

```text
AI

收入

60%

Storage

20%

API

20%
```

支持：

套餐分析：

```text
Free

Pro

Enterprise
```

转化率。

---

# 十三、Dashboard Runtime

首页：

CEO Dashboard。

展示：

```text
今日收入

MRR

ARR

新增客户

续费率

退款率

利润率

```

---

图表：

```text
收入趋势

██████

利润趋势

████

客户增长

███
```

---

# 十四、后台 UX

新增：

```text
商业中心

├── Revenue ⭐

├── Finance

├── KPI

├── Forecast

├── Reports

```

---

## 页面1：Revenue Dashboard

展示：

```text
收入

¥100000


利润

¥65000


退款

¥5000


增长

+12%
```

---

## 页面2：MRR Dashboard

展示：

```text
MRR

¥120000


ARR

¥1440000


Growth

+8%
```

---

## 页面3：Customer Analytics

展示：

```text
客户

ABC

MRR

10000

状态

Healthy
```

---

## 页面4：Product Analytics

展示：

```text
AI

60%

Storage

25%

API

15%
```

---

## 页面5：Forecast

展示：

```text
预测：

8月

收入：

¥130000
```

---

# 十五、用户端 UX

普通用户：

不需要：

企业财务。

仅展示：

```text
我的消费

我的账单

我的套餐

本月费用
```

Revenue：

仅：

管理员。

---

# 十六、安全设计

## 1. 财务权限

权限：

```text
FINANCE_VIEW

FINANCE_EXPORT

KPI_VIEW

REPORT_EXPORT
```

---

## 2. 数据隔离

租户：

只能：

查看：

自己的：

Revenue。

平台：

可以：

查看：

全部。

---

## 3. 快照不可修改

所有：

Snapshot：

Immutable。

---

# 十七、和其他 Core 模块集成

## core-ai

统计：

AI：

收入。

AI：

成本。

利润。

---

## core-payment

支付：

收入。

退款。

---

## core-subscription

MRR

ARR

Renewal

---

## core-notification

自动：

日报。

周报。

月报。

---

## core-audit

所有：

财务：

导出：

修改：

进入：

Audit。

---

# 十八、数据库设计总结

新增：

```text
billing_revenue_snapshot

billing_cost_record

billing_profit_snapshot

billing_kpi_snapshot

billing_forecast

billing_customer_metrics

billing_product_metrics
```

> **建议增加一个统一的 `billing_metrics_snapshot` 表作为长期演进方向**，将各种 KPI 统一建模，避免后续快照表越来越多。MVP 可以保留拆分表，企业版逐步收敛。

---

# 十九、MVP实现范围

## Backend

实现：

```text
Revenue Snapshot

KPI

Dashboard

Customer Analytics

Product Analytics
```

---

## Frontend

Admin：

```text
Revenue Dashboard

MRR Dashboard

Customer Dashboard

Product Dashboard

```

---

暂不实现：

❌ AI预测

❌ BI拖拽分析

❌ 数据仓库

❌ OLAP

❌ Power BI / Tableau

❌ 多法人集团财务

---

# 二十、企业级增强（Enterprise）

## ① Revenue Recognition（收入确认）

很多 SaaS：

收到：

```text
1200元
```

不能：

立即：

确认：

收入。

例如：

一年套餐：

```text
1200

↓

每月确认

100
```

支持：

ASC606 / IFRS15。

---

## ② 多组织财务

例如：

```text
中国

日本

美国
```

收入：

合并。

---

## ③ AI成本中心

例如：

```text
GPT-5

收入

100万

成本

60万

利润

40万
```

每个模型：

独立分析。

---

## ④ 财务驾驶舱

CEO：

首页：

```text
MRR

ARR

Cash

Growth

Burn Rate

Runway

Profit
```

---

## ⑤ 自定义指标体系

允许企业新增：

```text
每 Agent 收入

每 Workspace 收入

每 API Key 收入

每组织利润
```

形成统一指标平台，而不是把指标写死在代码里。

---

# 二十一、完成标准

Phase 8 完成：

✅ 支持 Revenue Dashboard
✅ 支持 MRR / ARR
✅ 支持利润分析
✅ 支持客户分析
✅ 支持产品分析
✅ 支持 Forecast（MVP）
✅ 支持日报 / 月报
✅ 支持企业经营分析

---

# 最终能力变化

Phase 7：

> 平台知道客户应该支付多少钱，并能完成结算。

Phase 8：

> 平台知道整个公司赚了多少钱、为什么赚钱、未来会赚多少钱。

完整商业运营闭环变为：

```text
Subscription
      │
      ▼
Quota
      │
      ▼
Metering
      │
      ▼
Pricing
      │
      ▼
Invoice
      │
      ▼
Payment
      │
      ▼
Settlement
      │
      ▼
Revenue
      │
      ▼
Finance Analytics
```

到这一阶段，`core-billing` 已经不只是一个 Billing 模块，而是演进为一个可支撑 **AI 平台、SaaS 平台、开放平台以及企业软件** 的**商业运营与财务分析平台**。其中 MVP 聚焦于收入统计、KPI 和基础分析，企业版再逐步引入收入确认、成本中心、集团财务和高级 BI 能力。
