# Phase 9：商业化

```
core-billing
```

## 核心定位

`core-billing` 是整个 Core Platform 的**商业价值闭环层**。

前面的模块解决：

```
用户是谁？
(core-user)

能做什么？
(core-auth)

数据在哪里？
(core-storage)

如何通知？
(core-notification)

AI 如何使用？
(core-ai)

如何自动化？
(core-workflow)

如何开放？
(core-openapi)
```

但是企业平台最终必须回答：

> 谁用了多少资源？
> 谁应该付多少钱？
> 如何收费？
> 如何控制成本？
> 如何形成商业模式？

所以：

```
core-billing
=
计费引擎
+
订阅系统
+
额度系统
+
支付系统
+
账务系统
+
商业分析
```

---

# 整体 RoadMap

```
                    Enterprise Billing Platform

                            ↑

                    Phase 9
              Commercial Platform Runtime

                            ↑

                    Phase 8
              Usage Analytics Runtime

                            ↑

                    Phase 7
              Invoice Runtime

                            ↑

                    Phase 6
              Payment Runtime

                            ↑

                    Phase 5
              Subscription Runtime

                            ↑

                    Phase 4
              Quota Runtime

                            ↑

                    Phase 3
              Metering Runtime

                            ↑

                    Phase 2
              Pricing Runtime

                            ↑

                    Phase 1
              Account Balance Runtime

                            ↑

                    Phase 0
              Billing Foundation Runtime
```

---

# Phase 0：Billing Foundation Runtime ⭐⭐⭐⭐⭐

## 目标

建立最基础的商业账本能力。

MVP 第一阶段不要接支付。

先解决：

```
用户
 ↓
使用资源
 ↓
产生费用
 ↓
形成账单
```

---

# 核心能力

## 1. Billing Account

建立商业账户。

例如：

```
User
 |
Organization
 |
Billing Account
```

表：

```
billing_account

id
tenant_id

account_type

PERSONAL
ORGANIZATION


status

created_time
```

---

## 2. Transaction Ledger

核心中的核心：

账本。

不要直接修改余额。

采用：

```
Ledger Pattern
```

类似银行。

例如：

充值：

```
+100
```

消费：

```
-20
```

最终：

```
balance = sum(transaction)
```

表：

```
billing_transaction


id

account_id

type

TOP_UP
CONSUME
REFUND


amount


reference_type

AI_REQUEST


reference_id


created_time
```

---

# UX

用户中心：

```
我的账户


余额

¥100


最近消费

AI模型调用
-¥5


存储空间
-¥2
```

---

# 为什么 P0？

因为商业系统最大的风险：

不是支付。

而是：

> 钱算错。

所以第一原则：

```
先有账
再有支付
```

---

# Phase 1：Account Balance Runtime ⭐⭐⭐⭐⭐

## 目标

支持余额体系。

类似：

* AWS Credits
* OpenAI Credits

---

能力：

```
账户余额

充值

扣费

冻结

退款
```

---

模型：

```
Balance

100


预扣:

-20


确认:

-20


失败:

恢复
```

支持：

```
冻结资金
```

例如：

AI请求：

```
预计消耗10元

冻结10元

执行

实际8元

释放2元
```

---

# 为什么重要？

未来：

AI Agent 自动执行任务。

不知道最终成本。

必须支持：

```
预授权
```

---

# Phase 2：Pricing Runtime ⭐⭐⭐⭐⭐

## 目标

价格模型。

因为不同资源：

不同价格。

例如：

AI:

```
GPT-5

input token
output token

价格
```

Storage:

```
GB/月
```

Notification:

```
SMS
Email
Phone
```

---

设计：

```
Pricing Rule


resource

AI_TOKEN


unit

1000_TOKEN


price

0.01
```

---

支持：

## 固定价格

```
短信

0.05/条
```

## 阶梯价格

例如：

```
0-100GB

0.1/G


100-1000GB

0.08/G
```

---

## 为什么现在做？

因为：

不要把价格写死代码。

否则：

```
修改价格
=
重新发布系统
```

---

# Phase 3：Metering Runtime ⭐⭐⭐⭐⭐

## 目标

资源计量。

这是 SaaS 商业化核心。

建立：

```
Usage Event
```

例如：

AI:

```
user_id

model=gpt5

input_token=1000

output_token=500
```

Storage:

```
file_size=100MB
```

API:

```
request_count=1000
```

---

架构：

```
业务系统

    |
    |

Usage Event

    |
    |

Meter Engine

    |
    |

Billing
```

---

表：

```
usage_record


id

tenant_id

resource_type

quantity

unit

time
```

---

# 为什么必须独立？

因为：

业务系统只负责：

```
记录使用
```

Billing负责：

```
计算价格
```

解耦。

---

# Phase 4：Quota Runtime ⭐⭐⭐⭐⭐

## 目标

额度控制。

类似：

OpenAI：

```
RPM
TPM
Credits
```

---

支持：

用户套餐：

```
Free


AI:

10000 token


Storage:

1GB


API:

10000次
```

---

模型：

```
Quota Plan

      |

Quota Item

      |

Usage
```

---

功能：

实时检查：

```
是否超额？
```

例如：

AI请求：

```
剩余额度:

1000 token


请求:

2000 token


拒绝
```

---

# Phase 5：Subscription Runtime ⭐⭐⭐⭐⭐

## 目标

订阅。

支持：

```
Free

Pro

Enterprise
```

---

模型：

```
Plan


    |

Subscription


    |

Renew Cycle

```

---

例如：

Pro:

```
$20/月


包含:

100万token

100GB存储

API访问
```

---

支持：

生命周期：

```
创建

试用

升级

降级

暂停

取消

续费
```

---

# Phase 6：Payment Runtime ⭐⭐⭐⭐☆

## 目标

真实支付。

接入：

* Stripe
* PayPal
* 国内支付

---

能力：

```
支付订单

支付渠道

支付回调

退款

对账
```

---

订单：

```
Payment Order


id

amount

status


PENDING

SUCCESS

FAILED
```

---

注意：

支付不是 Billing 核心。

支付只是：

```
资金入口
```

---

# Phase 7：Invoice Runtime ⭐⭐⭐⭐⭐

## 目标

企业发票。

企业客户必须需要。

---

能力：

```
账单周期

生成账单

下载发票

税务信息
```

---

例如：

每月：

```
2026-07账单


AI服务

$100


Storage

$20


API

$10


Total

$130

```

---

# Phase 8：Usage Analytics Runtime ⭐⭐⭐⭐⭐

## 目标

商业分析。

类似：

AWS Cost Explorer。

---

Dashboard：

```
成本趋势


AI成本

████████


存储成本

████


Top Users


1. User A
2. User B
```

---

能力：

```
成本预测

异常检测

预算控制
```

---

# Phase 9：Enterprise Billing Platform ⭐⭐⭐⭐⭐

## 目标

企业级商业平台。

最终：

```
Enterprise Customer


        |

Organization


        |

Contract


        |

Pricing


        |

Usage


        |

Invoice


        |

Payment
```

---

# 企业能力

## 1. 多租户计费

```
集团

 |
公司

 |
部门

 |
用户

```

---

## 2. 合同价格

例如：

客户A：

```
标准价

-30%

合同3年
```

---

## 3. 成本中心

例如：

研发：

```
AI成本 5000
```

市场：

```
AI成本 3000
```

---

## 4. Chargeback

内部收费。

例如：

集团：

```
IT部门提供AI服务

业务部门购买
```

---

# 最终模块结构

```
core-billing

├── billing-core
│
├── billing-account
│
├── billing-ledger
│
├── billing-pricing
│
├── billing-metering
│
├── billing-quota
│
├── billing-subscription
│
├── billing-payment
│
├── billing-invoice
│
├── billing-analytics
│
└── billing-console
```

---

# 为什么放在 Phase 9？

因为商业化依赖前面的所有能力。

正确顺序：

```
用户
 ↓
资源
 ↓
能力
 ↓
使用
 ↓
计量
 ↓
收费
```

错误顺序：

```
先做支付
 ↓
没人用
 ↓
没有收入模型
 ↓
废弃
```

---

# MVP 建议

实际上第一版不要做完整 Billing。

只做：

```
P0 Billing Foundation

+

P1 Balance

+

P2 Pricing

+

P3 Metering
```

即可支持：

* AI Token收费
* API调用收费
* 存储收费
* SaaS套餐

这已经超过大量早期 SaaS 平台。

---

# Core Platform 最终闭环

到这里：

```
core-user
      |
core-auth
      |
core-storage
      |
core-notification
      |
core-ai
      |
core-workflow
      |
core-openapi
      |
core-audit
      |
core-billing
      |
      ↓

完整 SaaS Platform Kernel
```

这时候 Core Platform 才真正具备：

> 从技术平台 → 商业平台 → 企业操作系统 的基础能力。
