# Phase 5：Subscription Runtime 详细设计

```text
core-billing-subscription
```

---

# 一、Phase 5 定位

## 核心目标

建立**产品套餐与订阅生命周期管理系统**。

前面的阶段：

| 阶段                      | 解决问题     |
| ----------------------- | -------- |
| P0 Billing Foundation   | 钱怎么记录    |
| P1 Balance Runtime      | 用户有什么钱   |
| P2 Pricing Runtime      | 资源多少钱    |
| P3 Metering Runtime     | 用户用了多少   |
| P4 Quota Runtime        | 用户还能用多少  |
| P5 Subscription Runtime | 用户买了什么服务 |

---

核心变化：

之前：

```text
用户
 |
单独购买资源
```

现在：

```text
用户

↓

购买套餐

↓

自动获得:

- 功能权限
- 资源额度
- 服务等级
- 价格优惠

```

---

# 二、为什么需要 Subscription？

没有订阅：

只能：

```text
AI Token
0.01/token

Storage
0.1/GB

API
0.001/request
```

这属于：

Usage Billing。

但是 SaaS 主流模式：

```text
Free

↓

Pro

↓

Business

↓

Enterprise

```

例如：

用户购买：

```text
Pro套餐

¥99/月


包含：

AI额度

100万Token


存储

100GB


高级功能

开启

```

所以：

Subscription 是商业产品层。

---

# 三、整体架构设计

```text
                    core-billing


                          |

                          ↓


                 Subscription Runtime


        +---------------+---------------+

        |                               |

        ↓                               ↓


      Plan                         Subscription


        |                               |

        ↓                               ↓


     Quota                         Billing


        |                               |

        ↓                               ↓


     Limits                        Payment


```

---

# 四、核心设计原则

## 原则1：套餐 ≠ 订单

很多系统混淆：

订单：

```text
一次购买行为
```

套餐：

```text
产品定义
```

例如：

Plan：

```text
Pro

¥99/月

```

Subscription：

```text
用户A购买Pro

开始:

2026-07-01

```

---

关系：

```text
Plan

 1

 |

 N

Subscription

```

---

# 原则2：订阅是状态机

订阅不是简单：

```text
active=true
```

生命周期：

```text
TRIAL

 ↓

ACTIVE

 ↓

PAUSED

 ↓

CANCELED

 ↓

EXPIRED

```

---

# 原则3：套餐变化不能影响历史

例如：

Pro套餐：

2026-07：

```text
100万Token
```

2026-09：

```text
200万Token
```

旧用户：

按照订阅快照。

所以：

Subscription必须保存：

```text
购买时版本
```

---

# 五、核心模块设计

```text
core-billing-subscription


├── Product Runtime

├── Plan Runtime

├── Plan Version

├── Subscription Runtime

├── Renewal Runtime

├── Upgrade/Downgrade

├── Trial Runtime

└── Subscription Console

```

---

# 六、Product Runtime

## 作用

定义商业产品。

例如：

```text
AI Assistant SaaS

Developer Platform

Enterprise Platform

```

---

表：

```sql
billing_product


id


product_code


product_name


description


status


created_time

```

---

示例：

```
product_code

AI_PLATFORM

```

---

# 七、Plan Runtime

## 作用

定义套餐。

例如：

```text
Free


Pro


Enterprise

```

---

表：

```sql
billing_plan


id


product_id


plan_code


plan_name


billing_cycle


status


created_time

```

---

billing_cycle：

```text
FREE

MONTHLY

YEARLY

CUSTOM

```

---

# 八、Plan Item Runtime

套餐包含什么。

例如：

Pro：

```text
AI Token

1000000


Storage

100GB


Feature:

AI Agent

```

---

表：

```sql
billing_plan_item


id


plan_id


item_type


resource_code


value


```

---

item_type：

```text
QUOTA

FEATURE

PRICE_DISCOUNT

SERVICE_LEVEL

```

---

例如：

额度：

```json
{
"type":"QUOTA",

"resource":"AI_TOKEN",

"value":1000000
}

```

---

功能：

```json
{
"type":"FEATURE",

"code":"ADVANCED_AGENT"

}

```

---

# 九、Plan Version Runtime

## 为什么需要版本？

套餐会变化。

例如：

Pro V1：

```text
100万Token
```

Pro V2：

```text
200万Token
```

---

表：

```sql
billing_plan_version


id


plan_id


version


effective_time


expire_time

```

---

---

# 十、Subscription Runtime

## 核心对象

用户订阅关系。

---

表：

```sql
billing_subscription


id


tenant_id


plan_id


plan_version


status


start_time


end_time


next_billing_time


created_time

```

---

示例：

```text
用户:

ABC公司


套餐:

Enterprise


状态:

ACTIVE


下次续费:

2026-08-01

```

---

# 十一、订阅创建流程

用户购买：

```text
选择套餐


↓

创建订单


↓

支付成功


↓

Subscription创建


↓

Quota分配


↓

Feature开启


```

---

流程：

```text
Payment

    |

    ↓

Subscription


    |

    +------------+

    |            |

    ↓            ↓


Quota        Feature


```

---

# 十二、Trial Runtime

## 免费试用

企业非常重要。

例如：

```text
Enterprise Trial

30天

```

---

状态：

```text
TRIAL

```

结束：

```text
ACTIVE

or

EXPIRED

```

---

字段：

```sql
trial_end_time
```

---

# 十三、Upgrade / Downgrade Runtime

## 升级

例如：

Free:

```text
↓

Pro
```

立即：

```text
增加额度

开启功能

```

---

## 降级

例如：

Enterprise:

↓

Pro

需要：

```text
检查当前使用量

```

如果：

```text
Storage

500GB


Pro限制:

100GB

```

不能直接降级。

---

# 十四、Subscription API设计

## 1. 查询套餐

```http
GET

/api/billing/plans

```

返回：

```json
[
{
"name":"Pro",

"price":99,

"cycle":"MONTHLY"
}
]

```

---

# 2. 创建订阅

```http
POST

/api/billing/subscriptions

```

请求：

```json
{
"plan":"PRO"
}

```

---

# 3. 查询当前订阅

```http
GET

/api/billing/subscription/current

```

返回：

```json
{
"plan":"PRO",

"status":"ACTIVE",

"expire":"2026-08-01"

}

```

---

# 十五、后台管理 UX

新增：

```
商业中心

├── 产品管理

├── 套餐管理 ⭐

├── 订阅管理

├── 价格管理

├── 使用量

```

---

# 页面1：套餐列表

展示：

```
Free

¥0


Pro

¥99/月


Enterprise

联系我们

```

---

# 页面2：套餐编辑

黑金企业风格：

```
Pro


价格:

99/月


包含:

✓ AI Agent

✓ 100万Token

✓ 100GB Storage


限制:

API 10万次

```

---

# 页面3：订阅管理

查看：

```
客户:

ABC公司


套餐:

Enterprise


状态:

ACTIVE


开始:

2026-07-01


续费:

2026-08-01

```

---

# 页面4：客户生命周期

类似 CRM：

```
Trial

 ↓

Paid

 ↓

Expansion

 ↓

Renewal

```

---

# 十六、用户端 UX

## 套餐中心

```text
我的套餐


当前:

Pro


¥99/月


下次续费:

2026-08-01



包含:


AI Token

800000/1000000


Storage

50GB/100GB


```

---

按钮：

```
升级套餐

取消订阅

查看账单

```

---

# 十七、安全设计

## 1. 套餐权限隔离

不能：

普通管理员修改价格。

权限：

```
PLAN_VIEW

PLAN_EDIT

PLAN_PUBLISH

SUBSCRIPTION_ADMIN

```

---

## 2. 发布机制

套餐：

不要直接修改。

流程：

```
Draft

↓

Review

↓

Published

↓

Archived

```

---

## 3. 防止订阅篡改

Subscription变化必须产生事件：

```
SUBSCRIPTION_CREATED

SUBSCRIPTION_UPGRADED

SUBSCRIPTION_CANCELLED

```

---

# 十八、和其他 Core 模块集成

## core-quota

最重要：

```text
Subscription


↓

Quota Allocation


↓

Resource Limit

```

---

## core-feature

未来增加：

Feature Flag。

例如：

Pro：

```text
AI Agent = ON

```

Free：

```text
OFF

```

---

## core-notification

通知：

```
订阅成功

续费提醒

过期提醒

```

---

# 十九、MVP实现范围

## Backend

必须：

```
Product

Plan

Plan Version

Subscription

Trial

Upgrade/Downgrade

```

---

## Frontend

用户：

```
套餐中心

我的订阅

升级页面

```

Admin：

```
套餐管理

订阅管理

```

---

# 暂不实现

❌ 自动续费支付

❌ 发票

❌ 企业合同

❌ 销售折扣

❌ 渠道分销

---

# 二十、完成标准

Phase 5 完成：

✅ 支持 Free/Pro/Enterprise 套餐
✅ 用户可以订阅套餐
✅ 自动获得额度
✅ 支持试用
✅ 支持升级降级
✅ 支持套餐版本管理
✅ 支持未来支付接入

---

# 最终能力变化

Phase 4：

> 用户还能使用多少资源。

Phase 5：

> 用户购买了什么服务。

完整 SaaS 商业模型：

```text
Subscription

决定身份


↓

Quota

决定限制


↓

Metering

记录使用


↓

Pricing

计算费用


↓

Balance

完成扣费


↓

Ledger

形成财务记录

```

完成 Phase 5 后，`core-billing` 从“计费系统”正式升级为 **SaaS 商业运营系统的核心引擎**。
