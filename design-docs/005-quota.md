# Phase 4：Quota Runtime 详细设计

```text
core-billing-quota
```

---

# 一、Phase 4 定位

## 核心目标

建立**资源额度控制系统**。

前面的阶段：

| 阶段                    | 解决问题    |
| --------------------- | ------- |
| P0 Billing Foundation | 钱怎么记录   |
| P1 Balance Runtime    | 用户有多少钱  |
| P2 Pricing Runtime    | 资源多少钱   |
| P3 Metering Runtime   | 用了多少    |
| P4 Quota Runtime      | 最多允许用多少 |

所以：

> Quota 是商业系统里的“边界控制层”。

它决定：

```text
用户还能不能继续使用服务？
```

---

# 二、为什么需要 Quota？

没有 Quota：

```text
用户

↓

无限调用 AI

↓

月底账单爆炸

↓

平台承担风险

```

---

有 Quota：

```text
用户套餐

↓

100万 Token额度


使用


↓

剩余10万


↓

达到限制


↓

提醒/拒绝/升级

```

---

# 三、整体架构设计

```text
                         Core Platform


                         User


                          |

                          ↓


                    core-billing


                          |

       +------------------+------------------+

       |                                     |

       ↓                                     ↓


  Metering Runtime                 Quota Runtime


       |                                     |

       ↓                                     ↓


 Usage Record                     Quota Counter


                          |

                          ↓


                    Allow / Reject

```

---

# 四、Quota 核心设计原则

## 原则1：Quota不是余额

很多人混淆：

Balance：

```text
钱
```

Quota：

```text
资源使用限制
```

例如：

用户：

余额：

```text
¥100
```

额度：

```text
AI Token:

100万

```

两个完全不同。

---

## 原则2：Quota必须实时

例如：

AI请求：

```text
剩余:

1000 Token


请求:

2000 Token

```

必须立即拒绝。

---

## 原则3：Quota支持多维度

企业复杂场景：

不是：

```text
一个额度
```

而是：

```text
用户

组织

套餐

API Key

Agent

模型

```

---

# 五、核心模块设计

```text
core-billing-quota


├── Quota Definition

├── Quota Plan

├── Quota Allocation

├── Quota Counter

├── Quota Check

├── Quota Alert

└── Quota Console

```

---

# 六、Quota Definition Runtime

## 作用

定义限制对象。

例如：

AI：

```text
AI_TOKEN
```

API：

```text
API_CALL
```

Storage：

```text
STORAGE_GB
```

---

表：

```sql
billing_quota_definition


id


resource_code


quota_name


unit


period


created_time

```

---

示例：

| 资源       | 单位    | 周期    |
| -------- | ----- | ----- |
| AI_TOKEN | TOKEN | MONTH |
| API_CALL | COUNT | DAY   |
| STORAGE  | GB    | MONTH |

---

# 七、Quota Plan Runtime

## 作用

套餐包含哪些额度。

例如：

Pro套餐：

```text
AI:

100万 Token/月


Storage:

50GB


API:

10万次/月

```

---

模型：

```text
Plan

  |

Quota Items

```

---

表：

```sql
billing_quota_plan


id


plan_id


resource_code


quota_limit


period

```

---

数据：

```text
PRO


AI_TOKEN

1000000

MONTH

```

---

# 八、Quota Allocation Runtime

## 作用

把额度分配给用户。

流程：

```text
套餐


↓

用户购买


↓

生成额度

```

---

表：

```sql
billing_quota_allocation


id


tenant_id


resource_code


quota_total


quota_used


expire_time

```

---

例如：

```text
用户A


AI_TOKEN


总额度:

1000000


已使用:

200000

```

---

# 九、Quota Counter Runtime

## 核心

实时计数器。

例如：

```text
AI_TOKEN


limit:

1000000


used:

800000


remaining:

200000

```

---

表：

```sql
billing_quota_counter


id


allocation_id


used_amount


updated_time

```

---

# 十、核心流程设计

# 场景1：AI调用额度检查

请求：

```text
用户请求GPT5

预计消耗:

5000 token

```

流程：

```text
core-ai


 |

Quota Check


 |

剩余额度?


 |

允许


 |

执行


 |

Metering记录


 |

Quota增加使用量

```

---

如果：

```text
剩余:

1000


请求:

5000

```

返回：

```json
{
code:"QUOTA_EXCEEDED",

message:"AI额度不足"

}

```

---

# 十一、Quota Check API

## 查询额度

```http
GET

/api/billing/quota/{tenantId}

```

返回：

```json
[
{
"resource":"AI_TOKEN",

"limit":1000000,

"used":200000,

"remaining":800000
}
]

```

---

# 十二、额度扣减设计

## 两阶段模式

不要：

直接减少。

错误：

```text
check

↓

consume

```

因为并发危险。

---

正确：

```text
Reserve


 ↓


Execute


 ↓


Commit


```

---

类似库存系统。

---

## Reserve

冻结额度：

```text
available:

10000


reserved:

5000

```

---

## Commit

成功：

```text
used:

+5000

reserved:

-5000

```

---

## Release

失败：

```text
reserved:

-5000

```

---

# 十三、Quota策略设计

不同用户：

不同策略。

---

## 策略1：阻断

适合：

免费用户。

```text
超过额度

↓

拒绝

```

---

## 策略2：超额付费

企业常用。

例如：

套餐：

100万Token

超过：

自动按量收费。

流程：

```text
Quota exhausted


↓

Metering


↓

Pricing


↓

Balance Consume

```

---

## 策略3：降级

例如：

AI：

GPT5额度用完

自动切换：

GPT4。

---

# 十四、Quota Alert Runtime

## 为什么需要？

用户不喜欢：

突然不能用。

---

提醒节点：

```text
50%

80%

90%

100%

```

---

通知：

接入：

```text
core-notification

```

---

例如：

邮件：

```text
您的AI额度已使用90%

```

---

# 十五、后台管理 UX

新增：

```text
商业中心


├── 账户

├── 余额

├── 价格

├── 使用量

├── 额度管理 ⭐

```

---

# 页面1：额度总览

```text
客户:


ABC公司


套餐:

Enterprise


--------------------------------


AI Token

80%

████████


API

30%


Storage

60%

```

---

# 页面2：额度配置

运营：

创建套餐。

例如：

```text
套餐:

Pro


额度:


AI Token

1000000/月


Storage

100GB


API

50000/月

```

---

# 页面3：额度使用详情

显示：

```text
资源:

AI_TOKEN


总额度:

100万


已使用:

80万


剩余:

20万


趋势:

增长

```

---

# 十六、用户端 UX

新增：

```text
使用额度

```

页面：

```text
我的套餐


Pro


AI Token


800000 / 1000000


80%


预计月底耗尽


```

---

按钮：

```text
升级套餐

```

---

# 十七、安全设计

## 1. 防止额度绕过

所有商业资源：

必须经过：

```text
Quota Check

```

---

例如：

API：

不能：

```text
直接调用AI

```

必须：

```text
API Gateway

↓

Quota

↓

AI

```

---

# 2. 防止并发超卖

必须：

```text
乐观锁

+

Reserve机制

```

---

# 3. 配额变更审计

记录：

```text
谁

什么时候

修改什么额度

修改前

修改后

原因

```

---

# 十八、数据库设计总结

新增：

```text
billing_quota_definition


billing_quota_plan


billing_quota_item


billing_quota_allocation


billing_quota_counter


billing_quota_record


billing_quota_alert

```

---

# 十九、和其他 Core 模块集成

## core-ai

最重要。

```text
AI Request


↓

Quota Check


↓

AI Execute


↓

Metering


↓

Billing

```

---

## core-openapi

```text
API Request


↓

API Quota


↓

Allow

```

---

## core-storage

```text
Upload


↓

Storage Quota


↓

Allow

```

---

## core-notification

```text
Quota Alert


↓

Email/SMS/IM

```

---

# 二十、MVP实现范围

## Backend

必须：

```text
Quota Definition

Quota Allocation

Quota Check

Quota Consume

Quota Alert

```

---

## Frontend

Admin：

```text
额度配置

额度查看

额度调整

```

用户：

```text
套餐额度

使用情况

```

---

# 暂不实现

❌ AI自动推荐额度

❌ 动态额度调整

❌ 多级复杂组织继承

❌ 实时大规模分布式计数

---

# 二十一、完成标准

Phase 4 完成后：

✅ 支持套餐额度
✅ 支持用户资源限制
✅ 支持实时检查
✅ 支持超额策略
✅ 支持额度提醒
✅ 支持 AI/API/Storage 控制

---

# 最终能力变化

Phase 3：

> 平台知道用户用了多少。

Phase 4：

> 平台知道用户还能用多少。

组合：

```text
P2 Pricing

资源多少钱


+

P3 Metering

用了多少


+

P4 Quota

还能用多少


=

完整 SaaS 使用控制系统

```

完成 Phase 4 后，`core-billing` 已经具备类似：

* OpenAI Usage Limit
* AWS Service Quota
* Stripe Metered Billing
* SaaS Plan Limit

的核心能力。
