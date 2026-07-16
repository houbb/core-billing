# Phase 3：Metering Runtime 详细设计

```text
core-billing-metering
```

---

# 一、Phase 3 定位

## 核心目标

建立**统一资源使用计量系统**。

前面的能力：

| 阶段                    | 解决问题   |
| --------------------- | ------ |
| P0 Billing Foundation | 钱如何记录  |
| P1 Balance            | 用户有多少钱 |
| P2 Pricing            | 资源值多少钱 |
| P3 Metering           | 用户用了多少 |

所以：

> Metering 是连接业务系统和商业系统之间的桥梁。

完整链路：

```text
业务行为

(core-ai / core-storage / core-openapi)

        |

        ↓

Usage Event

        |

        ↓

Metering Runtime

        |

        ↓

Usage Record

        |

        ↓

Pricing Runtime

        |

        ↓

Billing Transaction

        |

        ↓

Balance Consume

```

---

# 二、为什么必须独立 Metering？

很多系统早期：

业务代码直接扣费：

```java
billing.consume(
    userId,
    0.01
);
```

这是错误设计。

因为业务知道：

```
发生了什么
```

但是不知道：

```
应该多少钱
```

例如 AI：

业务只知道：

```json
{
model:"GPT5",
inputToken:10000,
outputToken:2000
}
```

Billing 才知道：

```
10000 token多少钱
```

所以分层：

```text
业务层

记录使用量


↓

Metering

统计使用量


↓

Pricing

计算价格


↓

Billing

产生账单

```

---

# 三、整体架构设计

```text
                         Core Platform


+-------------+      +--------------+

| core-ai     |      | core-storage |

+-------------+      +--------------+

        |                    |

        | Usage Event       |

        +---------+----------+

                  |

                  ↓


        core-billing-metering


                  |

        +---------+---------+

        |                   |

        ↓                   ↓


 Usage Collector       Usage Aggregator


        |

        ↓


 Usage Record


        |

        ↓


 Pricing Runtime


        |

        ↓


 Ledger

```

---

# 四、核心设计原则

# 原则1：计量事件不可丢

商业数据：

必须可靠。

例如：

AI调用：

```
消耗10000 token
```

如果丢失：

平台损失收入。

所以：

Usage Event 必须：

* 可追踪
* 可重放
* 可查询

---

# 原则2：Usage 是事实

类似账本。

不要：

```
当前使用量 = 10000
```

然后修改。

应该：

```
事件1:

+1000 token


事件2:

+9000 token


总量=10000

```

---

# 原则3：业务和计费解耦

业务：

```text
我产生了一次使用
```

Billing：

```text
我负责计算费用
```

---

# 五、核心模块设计

```text
billing-metering


├── Usage Event
│
├── Collector
│
├── Meter Definition
│
├── Usage Record
│
├── Aggregation
│
└── Meter Console

```

---

# 六、Usage Event Runtime

## 作用

接收业务使用事件。

---

例如 AI：

```json
{
event:"AI_COMPLETION",

tenantId:"001",

resource:"AI_TOKEN",

quantity:12000,

unit:"TOKEN",

metadata:{
model:"GPT5"
}
}
```

---

存储：

## billing_usage_event

```sql
CREATE TABLE billing_usage_event
(

id INTEGER PRIMARY KEY,


event_id VARCHAR(64),


tenant_id VARCHAR(64),


resource_code VARCHAR(64),


quantity DECIMAL(18,6),


unit VARCHAR(32),


metadata TEXT,


event_time DATETIME,


created_time DATETIME

);
```

---

字段：

| 字段            | 作用   |
| ------------- | ---- |
| event_id      | 幂等   |
| resource_code | 资源类型 |
| quantity      | 数量   |
| metadata      | 业务信息 |

---

# 七、Meter Definition Runtime

## 为什么需要定义 Meter？

因为不同资源：

统计方式不同。

例如：

AI:

```
token数量
```

Storage:

```
GB占用时间
```

API:

```
请求次数
```

---

模型：

```text
Meter


resource

+

measurement rule

```

---

表：

```sql
billing_meter_definition


id


resource_code


meter_name


unit


aggregation_type


status

```

---

aggregation_type：

```text
SUM

COUNT

MAX

AVERAGE

```

---

例如：

AI：

```text
SUM(token)

```

API：

```text
COUNT(request)

```

Storage：

```text
AVERAGE(GB)

```

---

# 八、Usage Record Runtime

## 作用

形成可计费使用记录。

Event:

```
1000 token

```

经过 Meter：

```
AI_TOKEN

1000

```

---

表：

```sql
billing_usage_record


id


tenant_id


resource_code


quantity


period


status


created_time

```

---

status：

```
PENDING

CALCULATED

BILLED

```

---

# 九、核心流程设计

# 场景1：AI调用计量

流程：

```
用户请求AI


↓

core-ai


↓

生成Usage Event


↓

Metering


↓

Usage Record


↓

Pricing计算


↓

Billing扣费

```

---

示例：

AI返回：

```
input:
10000 token


output:
2000 token

```

产生：

```json
{
resource:"AI_TOKEN",
quantity:12000
}

```

---

# 场景2：文件存储计量

每天凌晨：

```
扫描文件


↓

计算容量


↓

生成Usage

```

例如：

```
storage:

50GB

```

---

# 场景3：API调用计量

每次请求：

```
API_CALL +1

```

---

# 十、幂等设计

商业事件必须避免重复。

例如：

AI调用：

event_id:

```
ai_req_001

```

重复发送：

```
ai_req_001

ai_req_001

```

数据库：

```sql
unique(event_id)
```

保证：

只记录一次。

---

# 十一、实时计量 vs 批量计量

企业场景：

两种都有。

---

## 实时 Metering

适合：

* AI Token
* API调用

流程：

```
请求

↓

立即记录

```

---

## Batch Metering

适合：

* 存储
* 数据量

流程：

```
每天统计

↓

生成Usage

```

---

最终：

采用：

```
实时 + 批量

Hybrid

```

---

# 十二、后台管理 UX

新增：

```
商业中心


├── 账户

├── 余额

├── 价格

├── 使用量 ⭐

├── 流水

```

---

# 页面1：Usage Dashboard

展示：

```
今日使用量


AI Token

1,200,000


API调用

50,000


存储

200GB

```

---

# 页面2：资源使用趋势

图表：

```
AI Token

|
|
|       *
|    *
| *
+----------------

日期

```

---

# 页面3：Usage详情

类似：

日志。

```
时间:

15:20


资源:

AI_TOKEN


数量:

10000


来源:

core-ai


业务ID:

req001

```

---

# 页面4：异常检测

例如：

```
异常：

用户A

1小时调用AI

增长500%

```

---

# 十三、用户端 UX

用户看到：

```
使用情况


本月:


AI Token

500000 / 1000000


API

20000 / 50000


Storage

5GB / 10GB

```

---

注意：

这里只展示：

```
用了多少

```

不是：

```
怎么收费

```

价格属于 Pricing。

---

# 十四、安全设计

## 1. Usage不可修改

禁止：

```
update usage
```

只能：

```
追加修正事件

```

例如：

错误：

```
-1000

```

新增：

```
CORRECTION

-1000

```

---

## 2. 来源认证

防止业务伪造：

```
core-ai

发送Usage

```

需要：

Service Token。

---

## 3. 审计

记录：

```
谁产生

什么时候

来源服务

```

---

# 十五、和其他 Core 模块集成

## core-ai

最重要。

```text
AI Runtime


↓

Token Usage


↓

Metering


↓

Billing

```

---

## core-storage

```text
文件生命周期


↓

Storage Usage


↓

Metering

```

---

## core-openapi

```text
API Gateway


↓

Request Count


↓

Metering

```

---

# 十六、MVP实现范围

## Backend

实现：

```
Usage Event API

Meter Definition

Usage Record

Aggregation Job

```

---

## Frontend

Admin：

```
使用量管理

资源统计

Usage查询

```

用户：

```
我的使用情况

```

---

# 暂不实现

❌ 实时流计算

❌ Kafka

❌ Flink

❌ 大规模数据仓库

❌ AI成本预测

原因：

当前 SQLite + SpringBoot 足够。

---

# 十七、数据库总结

Phase 3：

新增：

```
billing_usage_event

billing_meter_definition

billing_usage_record

billing_usage_daily

```

---

# 十八、完成标准

Phase 3 完成后：

✅ 任意业务可以上报使用量
✅ 使用量统一管理
✅ 支持 AI / API / Storage
✅ 支持实时计量
✅ 支持批量统计
✅ 支持后续自动计费
✅ 支持企业成本分析

---

# 最终能力变化

Phase 2：

> 一个资源值多少钱。

Phase 3：

> 用户到底用了多少资源。

三者组合：

```text
P1 Balance

用户有多少钱


+

P2 Pricing

资源多少钱


+

P3 Metering

用了多少


=

完整商业计费闭环基础

```

完成 Phase 3 后，`core-billing` 已经具备 SaaS 平台最核心的 **Usage-Based Billing（按量计费）模型**，后面的 Quota、Subscription、Invoice 都会建立在这个基础之上。
