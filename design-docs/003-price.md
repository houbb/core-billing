# Phase 2：Pricing Runtime 详细设计

```text
core-billing-pricing
```

---

# 一、Phase 2 定位

## 核心目标

建立**统一价格规则引擎**。

Phase 0：

> 记录钱的变化。

Phase 1：

> 管理用户的钱。

Phase 2：

> 决定一个业务行为值多少钱。

即：

```text
业务资源
    |
    ↓
Usage Event
    |
    ↓
Pricing Engine
    |
    ↓
Cost
    |
    ↓
Billing Transaction
```

---

# 二、为什么 Pricing 放在 Billing 前面？

很多系统早期会犯一个错误：

直接在业务代码写价格。

例如：

```java
if(model.equals("gpt5")){
    cost = token * 0.00001;
}
```

后果：

* 改价格需要发版
* 不支持不同客户价格
* 无法做促销
* 无法做套餐优惠
* 无法做企业合同价

正确方式：

业务只告诉 Billing：

```json
{
 "resource":"AI_TOKEN",
 "quantity":10000
}
```

Pricing 决定：

```json
{
 "price":0.2
}
```

---

# 三、整体架构

```text
                  Core Platform


core-ai
   |
   |
Usage Event

   |
   ↓


core-billing


   |
   +----------------+
   |                |
   ↓                ↓


Metering       Pricing Runtime


                    |

                    ↓


                Cost Result


                    |

                    ↓


                 Ledger


```

---

# 四、核心设计原则

## 原则1：价格配置化

价格不是代码。

错误：

```text
GPT5 = 0.01
```

正确：

```text
Pricing Rule

resource:
AI_TOKEN


model:
GPT5


unit:
1000 TOKEN


price:
0.01

```

---

# 原则2：价格版本化

价格会变化。

例如：

2026-07：

```text
GPT5

0.01/token
```

2026-10：

```text
GPT5

0.008/token
```

历史订单不能改变。

所以：

```text
Pricing Version
```

---

# 原则3：价格计算可解释

任何费用必须回答：

为什么是这个价格？

例如：

```text
AI调用费用:


输入Token:
10000


单价:
0.001/1000


费用:
0.01


价格规则:
GPT5-2026-Q3

```

---

# 五、核心模块设计

```text
pricing-runtime


├── Price Definition
│
├── Price Rule
│
├── Price Version
│
├── Calculator
│
├── Discount
│
└── Pricing Console

```

---

# 六、Price Definition Runtime

## 作用

定义收费对象。

例如：

AI：

```text
AI_TOKEN
```

存储：

```text
STORAGE_GB
```

API：

```text
API_CALL
```

短信：

```text
SMS_COUNT
```

---

## 数据表

## billing_resource

```sql
CREATE TABLE billing_resource
(

id INTEGER PRIMARY KEY,


resource_code VARCHAR(64),


resource_name VARCHAR(128),


unit VARCHAR(32),


status VARCHAR(32),


created_time DATETIME

);
```

---

数据：

| code       | name   | unit  |
| ---------- | ------ | ----- |
| AI_TOKEN   | AI模型调用 | TOKEN |
| STORAGE_GB | 存储空间   | GB    |
| API_CALL   | 接口调用   | COUNT |

---

# 七、Pricing Rule Runtime

## 核心模型

```text
Resource


    |

Pricing Rule


    |

Price Item

```

---

例如：

GPT5：

```text
Resource:

AI_TOKEN


Rule:

GPT5


Unit:

1000 Token


Price:

0.01

```

---

# 数据表

## billing_price_rule

```sql
CREATE TABLE billing_price_rule
(

id INTEGER PRIMARY KEY,


resource_code VARCHAR(64),


rule_name VARCHAR(128),


condition_json TEXT,


status VARCHAR(32),


created_time DATETIME

);
```

---

condition 示例：

```json
{
 "model":"GPT5",
 "region":"US"
}
```

---

# 八、价格版本设计

## 为什么需要版本？

例如：

今天：

```text
1000 Token = 0.01
```

明天：

```text
1000 Token = 0.008
```

历史账单：

必须保持：

```text
0.01
```

---

表：

```sql
billing_price_version


id


rule_id


price


effective_time


expire_time

```

---

# 九、计价算法设计

## 1. 固定价格

例如：

短信：

```text
一条=0.05
```

公式：

```text
cost = quantity * price
```

---

## 2. 单位价格

例如：

AI Token

```text
1000 Token

=

0.01
```

计算：

```text
cost

=

quantity / unit

*

price

```

---

## 3. 阶梯价格

企业常见。

例如：

存储：

```text
0-100GB

0.1/G


100-1000GB

0.08/G


1000+

0.05/G

```

---

模型：

```text
Tier Pricing
```

---

# 十、Pricing API设计

## 1. 查询价格

```http
GET

/api/billing/pricing/{resource}

```

返回：

```json
{
"resource":"AI_TOKEN",

"unit":"1000",

"price":0.01
}
```

---

# 2. 计算价格

核心接口。

```http
POST

/api/billing/pricing/calculate

```

请求：

```json
{
"resource":"AI_TOKEN",

"quantity":10000,

"context":{

"model":"GPT5"

}

}
```

---

返回：

```json
{
"cost":0.1,

"rule":

"GPT5-2026-Q3"

}
```

---

# 十一、后台管理 UX

新增：

```text
商业中心


├── 账户管理

├── 余额管理

├── 价格管理 ⭐

├── 交易流水

```

---

# 页面1：资源列表

```text
收费资源


AI Token

Storage

API Call

SMS

```

---

# 页面2：价格规则

例如：

```text
GPT5 Token收费


资源:

AI_TOKEN


条件:

model=GPT5


价格:

0.01 /1000 Token


状态:

启用


```

---

# 页面3：价格版本

展示：

```text
GPT5价格历史


版本1

2026-01

0.01


版本2

2026-07

0.008


```

---

# 页面4：价格模拟器

非常重要。

运营修改价格前：

先模拟。

输入：

```text
用户:

A


资源:

AI_TOKEN


数量:

100000

```

输出：

```text
预计费用:

1元


旧价格:

2元


变化:

-50%

```

---

# 十二、用户端 UX

普通用户不需要看到复杂规则。

显示：

```text
费用说明


AI模型费用


GPT5


输入:

¥0.001/1000 Token


输出:

¥0.005/1000 Token


```

---

# 十三、安全设计

## 1. 价格修改权限

价格属于高风险操作。

权限：

```text
PRICE_VIEW

PRICE_EDIT

PRICE_APPROVE
```

---

## 2. 修改必须留痕

例如：

管理员：

修改：

```text
GPT5

0.01

↓

0.008

```

记录：

```text
谁

什么时候

为什么

修改前

修改后

```

---

## 3. 禁止删除价格

只能：

```text
ENABLE

DISABLE

```

不能：

```text
DELETE
```

原因：

历史账单依赖。

---

# 十四、和其他 Core 模块结合

## core-ai

未来：

```text
AI Request

 ↓

Usage Event


 ↓

Pricing


 ↓

Cost


 ↓

Balance Consume

```

---

## core-storage

```text
File Size

 ↓

Storage Resource


 ↓

Pricing


 ↓

Cost

```

---

## core-openapi

```text
API Request

 ↓

API_CALL

 ↓

Pricing

```

---

# 十五、MVP 实现范围

必须：

## Backend

```text
Resource定义

Price Rule

Price Version

Calculate API

```

---

## Frontend

Admin：

```text
价格管理

价格编辑

价格历史

价格模拟
```

---

用户：

```text
费用说明
```

---

暂不实现：

❌ 动态竞价

❌ AI自动调价

❌ 复杂合同价格

❌ 多币种汇率

❌ 税费计算

---

# 十六、完成标准

Phase 2 完成后：

✅ 平台所有资源可定义价格
✅ 价格不写代码
✅ 支持价格变更
✅ 支持历史价格追踪
✅ 支持费用预估
✅ 支持 AI / Storage / API 接入

---

# 最终能力变化

Phase 1：

```text
用户有多少钱？
```

Phase 2：

```text
一个行为值多少钱？
```

到这里：

`core-billing` 开始具备真正 SaaS 商业化能力。

后续：

* Phase 3 Metering Runtime：统计用户用了多少
* Phase 4 Quota Runtime：限制用户最多用多少
* Phase 5 Subscription Runtime：定义套餐卖多少钱

这三层组合起来，就是现代 SaaS 的商业引擎。
