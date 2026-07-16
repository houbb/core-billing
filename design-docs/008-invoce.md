# Phase 7：Invoice Runtime 详细设计

```text
core-billing-invoice
```

---

# 一、Phase 7 定位

## 核心目标

建立**统一账单（Invoice）与结算（Settlement）系统**。

到了 Phase 7，Billing 开始从**支付平台**升级为**财务运营平台**。

前面的能力：

| Phase | 解决问题     |
| ----- | -------- |
| P0    | 如何记账     |
| P1    | 如何管理余额   |
| P2    | 如何定义价格   |
| P3    | 如何统计用量   |
| P4    | 如何限制额度   |
| P5    | 如何管理套餐   |
| P6    | 如何完成支付   |
| P7    | 如何生成客户账单 |

一句话：

> Payment 是一次交易，而 Invoice 是一段时间的结算结果。

---

# 二、为什么 Invoice 独立？

很多系统：

支付成功：

```text
订单

↓

完成
```

结束了。

企业不会。

企业真正流程：

```text
整个7月

↓

AI

Storage

API

SMS

↓

汇总

↓

Invoice

↓

客户付款
```

例如：

AWS：

```text
7月份

AI

$120

Storage

$30

API

$15

----------------

Invoice

$165
```

OpenAI：

月底：

生成：

```text
Invoice

Usage

Tax

Total
```

所以：

Invoice：

是商业运营必备。

---

# 三、整体架构

```text
                 core-billing


                      |

                      ↓


              Invoice Runtime


      +----------+----------+----------+

      |          |          |          |

      ↓          ↓          ↓          ↓


   Invoice   Settlement   Tax   Statement


                      |

                      ↓


                Payment Runtime
```

---

# 四、核心设计原则

## 原则1：Invoice来自Billing

Invoice：

不是：

```text
人工填写
```

而是：

自动生成：

```text
Ledger

+

Usage

+

Subscription

↓

Invoice
```

---

## 原则2：Invoice不可修改

生成：

之后：

不能：

```text
Update Amount
```

如果：

发现错误：

应该：

```text
Credit Note

Adjustment Invoice
```

而不是：

修改历史。

---

## 原则3：一个周期一个Invoice

例如：

按月：

```text
2026-07

↓

Invoice
```

按年：

```text
2026

↓

Invoice
```

保持：

财务一致。

---

# 五、核心模块设计

```text
core-billing-invoice


├── Invoice Runtime

├── Invoice Item

├── Statement Runtime

├── Settlement Runtime

├── Credit Note

├── Tax Runtime

├── Invoice Export

└── Invoice Console
```

---

# 六、Invoice Runtime

核心对象：

```text
Invoice
```

数据库：

```sql
billing_invoice

id

invoice_no

tenant_id

billing_period

currency

subtotal

tax

discount

total

status

created_time
```

---

status：

```text
DRAFT

GENERATED

SENT

PAID

OVERDUE

CLOSED
```

---

例如：

```text
Invoice

202607001

July

$165
```

---

# 七、Invoice Item Runtime

账单：

由：

多个：

Item：

组成。

例如：

```text
Invoice


AI Usage

$120


Storage

$30


API

$15
```

数据库：

```sql
billing_invoice_item

id

invoice_id

resource_code

description

quantity

unit_price

amount
```

---

示例：

| 资源       | 数量    | 金额  |
| -------- | ----- | --- |
| AI Token | 120万  | 120 |
| Storage  | 300GB | 30  |
| API      | 150万  | 15  |

---

# 八、Statement Runtime

Statement：

更加偏：

账户流水。

例如：

```text
7月

充值

+100

AI消费

-20

退款

+10

余额

90
```

企业：

经常需要：

```text
Account Statement
```

而不是：

Invoice。

数据库：

```sql
billing_statement

id

tenant_id

period

opening_balance

closing_balance

created_time
```

---

# 九、Settlement Runtime

Settlement：

负责：

真正结算。

例如：

月底：

```text
Usage

↓

Invoice

↓

Payment

↓

Settlement

↓

Closed
```

状态：

```text
PENDING

PROCESSING

SUCCESS

FAILED
```

---

# 十、Credit Note Runtime

企业：

不能：

删除Invoice。

如果：

退款：

应该：

生成：

```text
Credit Note
```

例如：

Invoice：

```text
100
```

退款：

```text
20
```

生成：

```text
Credit Note

-20
```

最终：

```text
Invoice

100

Credit

20

Remaining

80
```

---

# 十一、Tax Runtime

MVP：

税率：

固定。

例如：

```text
VAT

10%
```

企业：

支持：

```text
VAT

GST

Sales Tax

No Tax
```

数据库：

```sql
billing_tax_rule

id

country

tax_type

rate
```

---

# 十二、Invoice生成流程

月底：

Job：

```text
Metering

↓

Pricing

↓

Ledger

↓

Invoice Item

↓

Invoice

↓

Notification
```

如果：

客户：

自动扣费：

```text
Invoice

↓

Payment

↓

Settlement
```

否则：

等待：

付款。

---

# 十三、导出能力

企业：

必须：

支持：

```text
PDF

Excel

CSV
```

未来：

支持：

```text
电子发票

电子税票
```

---

# 十四、后台 UX

菜单：

```text
商业中心

├── Invoice ⭐

├── Statement

├── Settlement

├── Credit Note

├── Tax Rule
```

---

# 页面1：Invoice列表

展示：

| Invoice | 客户  | 金额  | 状态      |
| ------- | --- | --- | ------- |
| INV001  | ABC | 165 | PAID    |
| INV002  | XYZ | 300 | OVERDUE |

支持：

```text
查看

下载

重新发送
```

---

# 页面2：Invoice详情

展示：

```text
Invoice

July

------------------

AI

120


Storage

30


API

15


Tax

10


Total

175
```

支持：

```text
下载PDF

下载Excel
```

---

# 页面3：Statement

展示：

```text
Opening

100


Recharge

200


Usage

150


Closing

150
```

---

# 页面4：Settlement

展示：

```text
Invoice

INV001


Payment

Stripe


Status

SUCCESS
```

---

# 页面5：Tax Rule

运营：

维护：

```text
Country

Tax

Rate
```

---

# 十五、用户端 UX

新增：

```text
账单中心
```

展示：

```text
July

Invoice

$165

PAID
```

点击：

查看：

详细：

```text
AI

Storage

API

Tax

Discount

Total
```

按钮：

```text
下载PDF

下载CSV

立即支付
```

---

# 十六、安全设计

## 1.Invoice生成不可重复

例如：

```text
2026-07
```

只能：

生成：

一次。

数据库：

```sql
unique(
tenant_id,
billing_period
)
```

---

## 2.Invoice生成后不可修改

修改：

必须：

生成：

```text
Adjustment
```

---

## 3.Statement不可删除

所有：

Statement：

永久：

保存。

---

## 4.导出权限

权限：

```text
INVOICE_VIEW

INVOICE_EXPORT

SETTLEMENT_VIEW
```

---

# 十七、和其他 Core 模块集成

## core-payment

Invoice：

付款。

---

## core-notification

Invoice：

发送：

邮件。

---

## core-storage

Invoice：

PDF：

保存。

---

## core-audit

所有：

```text
Invoice

Export

Settlement

Tax
```

进入：

Audit。

---

# 十八、数据库设计总结

新增：

```text
billing_invoice

billing_invoice_item

billing_statement

billing_settlement

billing_credit_note

billing_tax_rule
```

---

# 十九、MVP实现范围

Backend：

```text
Invoice

Invoice Item

Statement

Settlement

PDF Export
```

Frontend：

Admin：

```text
Invoice管理

Statement

Settlement
```

用户：

```text
账单中心

下载PDF
```

---

暂不实现：

❌ 多税率自动计算

❌ 国家税务接口

❌ 自动开票

❌ ERP同步

❌ SAP/Oracle集成

---

# 二十、企业级增强（Enterprise）

建议在 Enterprise 版本增加：

### 1. Invoice Workflow

```text
Draft

↓

Review

↓

Approved

↓

Sent

↓

Paid
```

支持财务审批。

---

### 2. 多法人（Legal Entity）

例如：

```text
OpenAI US

OpenAI Europe

OpenAI Japan
```

不同法人：

生成：

不同Invoice。

---

### 3. 多币种

支持：

```text
USD

EUR

JPY

CNY
```

自动：

汇率。

---

### 4. 企业合同

例如：

```text
合同价

固定月费

+

超额Usage
```

支持：

混合计费。

---

### 5. ERP集成

例如：

```text
SAP

Oracle

Kingdee（金蝶）

Yonyou（用友）
```

Invoice：

自动同步。

---

# 二十一、完成标准

Phase 7 完成：

✅ 自动生成账单
✅ 自动汇总 Usage、Subscription、Payment
✅ 支持 Statement（账户对账单）
✅ 支持 Settlement（结算）
✅ 支持 Credit Note（贷项通知单）
✅ 支持 PDF / Excel 导出
✅ 支持税率计算（MVP）
✅ 支持企业财务扩展能力

---

# 最终能力变化

Phase 6：

> 平台能够完成一次支付。

Phase 7：

> 平台能够完成一个完整结算周期。

完整商业闭环变为：

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
Ledger
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
Statement
```

至此，`core-billing` 已经从单纯的支付能力，升级为一个能够支撑 **SaaS、AI 平台、API 平台以及企业软件** 的完整商业结算系统。
