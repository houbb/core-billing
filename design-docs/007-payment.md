# Phase 6：Payment Runtime 详细设计

```text
core-billing-payment
```

---

# 一、Phase 6 定位

## 核心目标

建立**统一支付运行时（Payment Runtime）**。

需要强调一点：

> **Payment ≠ Billing。**

很多系统一开始就把两者混在一起，这是后续无法扩展的根本原因。

Billing 负责：

> 算钱。

Payment 负责：

> 收钱。

二者之间通过 **Payment Order（支付订单）** 解耦。

整体关系：

```text
Usage

↓

Metering

↓

Pricing

↓

Billing

↓

Payment Order

↓

Payment Channel

↓

Callback

↓

Ledger

↓

Balance
```

所以：

**Payment 是 Billing 的资金入口，而不是 Billing 本身。**

---

# 二、为什么 Payment 放在 Phase 6？

因为真正的平台商业化顺序应该是：

```text
先知道：

用了多少

↓

多少钱

↓

欠多少钱

↓

最后才付款
```

而不是：

```text
先接 Stripe

↓

没人付钱

↓

没人用

↓

系统废弃
```

这是很多创业产品都会踩的坑。

---

# 三、整体架构

```text
                core-billing


                     |

                     ↓

             Payment Runtime


+-------------+-------------+-------------+

|             |             |             |

↓             ↓             ↓             ↓

Order      Channel      Callback      Refund


                     |

                     ↓

                Ledger Runtime


                     |

                     ↓

                 Balance
```

---

# 四、核心设计原则

## 原则1：支付订单唯一

平台中：

一次支付

永远只有：

```text
一个 Payment Order
```

支付渠道：

可以多个。

例如：

```text
订单

↓

Stripe失败

↓

重新使用 PayPal

↓

订单不变
```

不要：

```text
Stripe订单

PayPal订单

微信订单

支付宝订单

全部变成业务订单
```

业务永远只认识：

```text
Payment Order
```

---

## 原则2：支付渠道可插拔

Payment Runtime：

不依赖：

```text
Stripe
```

而是：

```text
Payment Driver
```

例如：

```text
Stripe

PayPal

微信支付

支付宝

Apple Pay

Google Pay
```

未来：

新增：

只需要：

```text
Payment SPI
```

即可。

---

## 原则3：支付结果以回调为准

错误：

```text
用户点击支付成功

↓

充值成功
```

正确：

```text
用户支付

↓

等待支付平台回调

↓

验证签名

↓

Ledger

↓

Balance

↓

成功
```

---

# 五、核心模块设计

```text
core-billing-payment

├── Payment Order
├── Payment Channel
├── Payment Driver SPI
├── Callback Runtime
├── Refund Runtime
├── Reconciliation Runtime
├── Payment Console
└── Payment SDK
```

---

# 六、Payment Order Runtime

## 核心对象

整个支付生命周期：

围绕：

```text
Payment Order
```

展开。

---

数据库：

```sql
billing_payment_order

id

order_no

tenant_id

business_type

business_id

amount

currency

status

channel

created_time
```

---

status：

```text
CREATED

PENDING

SUCCESS

FAILED

CLOSED

REFUNDED
```

---

业务关系：

例如：

```text
Subscription

↓

Payment Order

↓

Stripe

↓

SUCCESS
```

---

# 七、Payment Channel Runtime

支持：

统一渠道管理。

例如：

```text
Stripe

PayPal

微信

支付宝
```

每个渠道：

配置：

```text
Merchant ID

API Key

Webhook Secret

Environment
```

数据库：

```sql
billing_payment_channel

id

channel_code

channel_name

status

config_json
```

---

建议：

配置不要放数据库明文。

使用：

```text
core-config

Secret Runtime
```

保存密钥。

---

# 八、Payment Driver SPI

未来：

每个支付：

一个 Driver。

统一接口：

```java
PaymentDriver

create()

query()

cancel()

refund()

callback()
```

例如：

```text
StripeDriver

PayPalDriver

WechatDriver

AlipayDriver
```

Spring Boot：

建议：

SPI +

AutoConfiguration。

---

# 九、支付流程设计

## 场景1：余额充值

流程：

```text
用户

↓

充值100元

↓

Payment Order

↓

Stripe

↓

支付页面

↓

支付成功

↓

Webhook

↓

Ledger +100

↓

Balance +100
```

---

## 场景2：购买套餐

流程：

```text
用户购买Pro

↓

Payment Order

↓

支付

↓

成功

↓

Subscription

↓

Quota

↓

Feature
```

---

## 场景3：支付失败

```text
Payment Failed

↓

Order=FAILED

↓

Subscription 不创建

↓

Balance 不变化
```

---

# 十、Callback Runtime

这是：

Payment 最重要模块。

---

流程：

```text
Stripe

↓

Webhook

↓

Signature Verify

↓

Idempotent Check

↓

Update Order

↓

Ledger

↓

Balance
```

---

必须：

验证：

```text
签名

时间戳

订单号

金额
```

---

# 十一、Refund Runtime

退款：

不能：

直接：

```text
Balance -100
```

应该：

```text
Refund Request

↓

Payment Channel

↓

Refund Success

↓

Ledger

↓

Balance

↓

Refund Record
```

---

数据库：

```sql
billing_refund

id

refund_no

payment_order

amount

status

created_time
```

---

# 十二、Reconciliation Runtime（对账）

企业级必须有。

每天：

```text
Stripe账单

↓

本地订单

↓

自动比对

↓

差异报告
```

结果：

```text
SUCCESS

MISSING

AMOUNT_NOT_MATCH

UNKNOWN
```

---

页面：

```text
支付订单

平台订单

金额

结果
```

---

# 十三、后台 UX

菜单：

```text
商业中心

├── 支付订单 ⭐
├── 支付渠道
├── 退款管理
├── 对账中心
├── 支付日志
```

---

# 页面1：支付订单

展示：

| 订单号   | 金额  | 渠道     | 状态      |
| ----- | --- | ------ | ------- |
| PO001 | 99  | Stripe | SUCCESS |
| PO002 | 199 | PayPal | FAILED  |

---

点击：

查看：

```text
支付详情

↓

支付日志

↓

回调日志

↓

Ledger

↓

Balance
```

形成：

完整链路。

---

# 页面2：支付渠道

展示：

```text
Stripe

状态：

Enabled

Sandbox
```

支持：

```text
测试模式

生产模式
```

切换。

---

# 页面3：退款管理

```text
退款单号

订单号

金额

状态

原因
```

支持：

```text
发起退款

查看退款

重新同步
```

---

# 页面4：Webhook日志

企业级非常重要。

展示：

```text
时间

来源IP

渠道

签名验证

状态

错误原因
```

方便定位支付问题。

---

# 十四、用户端 UX

新增：

```text
我的支付
```

页面：

```text
充值记录

购买记录

退款记录

支付方式
```

例如：

```text
2026-07-16

充值

¥100

Stripe

成功
```

---

# 十五、安全设计

## 1. 幂等

支付回调：

可能：

```text
收到3次
```

只能：

执行一次。

数据库：

```sql
unique(channel,transaction_no)
```

---

## 2. 金额校验

不能：

相信：

```text
客户端金额
```

必须：

查询：

```text
Payment Order

金额

↓

比较

↓

一致才成功
```

---

## 3. Webhook签名

必须：

验证：

```text
Signature

Timestamp

Nonce
```

否则：

拒绝。

---

## 4. 支付日志不可删除

所有：

支付：

退款：

回调：

永久保留。

只能：

Archive。

不能：

Delete。

---

# 十六、和其他 Core 模块集成

## core-config

读取：

```text
Stripe Key

Webhook Secret
```

---

## core-user

用户：

支付账户。

---

## core-notification

支付：

成功：

失败：

退款：

通知。

---

## core-subscription

支付成功：

创建：

Subscription。

---

## core-audit

记录：

```text
支付

退款

回调

人工关闭

重新同步
```

全部进入：

Audit。

---

# 十七、MVP实现范围

Backend：

```text
Payment Order

Payment Channel

Callback

Refund

Balance Integration
```

---

Frontend：

Admin：

```text
订单

退款

Webhook

支付渠道
```

用户：

```text
充值

支付记录

退款记录
```

---

暂不实现：

❌ 自动扣款（Auto Pay）

❌ 多商户（Multi Merchant）

❌ 分账（Split Payment）

❌ 国际税务（VAT/GST）

❌ 风控引擎

这些放到企业版。

---

# 十八、数据库设计总结

新增：

```text
billing_payment_order

billing_payment_channel

billing_payment_callback

billing_refund

billing_reconciliation

billing_payment_log
```

---

# 十九、完成标准

Phase 6 完成：

✅ 支持统一支付订单
✅ 支持支付渠道插件化
✅ 支持 Webhook 回调
✅ 支持退款流程
✅ 支持每日对账
✅ 支持 Ledger、Balance 联动
✅ 支持 Subscription 自动开通
✅ 支持完整支付审计

---

# 二十、企业级建议（区别于 MVP）

这一阶段建议在 MVP 中**只接入一种支付渠道**（例如 Stripe），但架构必须一次设计到位。

建议提前预留以下扩展点：

| 扩展能力 | MVP  | 企业版         |
| ---- | ---- | ----------- |
| 支付渠道 | 单渠道  | 多渠道自动切换     |
| 商户   | 单商户  | 多商户、多租户     |
| 币种   | 单币种  | 多币种、汇率转换    |
| 退款   | 全额退款 | 部分退款、批量退款   |
| 对账   | 手动查看 | 自动对账、异常工单   |
| 风控   | 无    | 风险评分、黑名单、限额 |

这样，Phase 6 完成后，`core-billing` 就具备了完整的**资金流闭环**：

```text
Subscription
      │
      ▼
Payment Order
      │
      ▼
Payment Channel
      │
      ▼
Callback
      │
      ▼
Ledger
      │
      ▼
Balance
      │
      ▼
Quota / Feature 生效
```

从这一阶段开始，平台已经具备真正对外收费和持续运营 SaaS 产品的能力。
