# Phase 0：Billing Foundation Runtime 详细设计

```text
core-billing-billing-foundation
```

## 一、Phase 0 定位

### 核心目标

建立 **统一商业账本基础设施**。

这一阶段不做：

* ❌ 支付渠道
* ❌ 自动扣款
* ❌ 订阅套餐
* ❌ 发票
* ❌ 复杂价格计算

只解决一个最核心问题：

> 平台中所有产生价值交换的行为，都必须能够被准确记录、追踪、审计。

类似银行系统：

```
业务行为
   |
   ↓
产生交易事件
   |
   ↓
Billing Ledger
   |
   ↓
形成账户余额
```

---

# 一、整体架构设计

## 1. 系统位置

```
                 Core Platform


core-user
    |
    |
    ↓

core-billing


    |
    |
    +----------------+
    |                |
    ↓                ↓

Account          Ledger

账户             账本


```

---

# 二、核心设计原则

## 原则 1：账本不可修改

金融系统第一原则：

> 不修改历史，只追加记录。

错误：

```
余额 = 100

直接修改

余额 = 80
```

正确：

```
Transaction


+100

-20


最终余额=80
```

---

## 原则 2：所有金额必须可追溯

任何金额：

必须回答：

```
谁？

什么时候？

为什么？

产生什么业务？

多少钱？
```

例如：

```
用户A

2026-07-16 15:00


调用 GPT-5


消耗 1000 token


费用 0.02元

```

---

## 原则 3：业务系统不直接操作余额

错误：

```
core-ai

update balance -10
```

正确：

```
core-ai

产生 Usage Event


        ↓


core-billing

计算账单


        ↓


Ledger记录

```

---

# 三、模块设计

## Module 1：Billing Account Runtime

账户管理。

类似银行账户。

---

## Account 类型

### Personal Account

个人账户

```
User

 |

Billing Account

```

---

### Organization Account

企业账户

```
Organization

       |

Billing Account

```

---

## 数据模型

### billing_account

```sql
CREATE TABLE billing_account
(
    id INTEGER PRIMARY KEY,

    tenant_id VARCHAR(64),

    account_name VARCHAR(128),

    account_type VARCHAR(32),
    
    status VARCHAR(32),

    created_time DATETIME,

    updated_time DATETIME
);
```

---

字段说明：

| 字段           | 说明    |
| ------------ | ----- |
| tenant_id    | 租户    |
| account_name | 账户名称  |
| account_type | 个人/企业 |
| status       | 状态    |

---

# Module 2：Ledger Runtime

核心模块。

## Ledger模型

```
账户

   |

交易流水


   |

余额

```

---

## Transaction

### 数据表

```sql
CREATE TABLE billing_transaction
(

id INTEGER PRIMARY KEY,


account_id INTEGER,


transaction_no VARCHAR(64),


transaction_type VARCHAR(32),


amount DECIMAL(18,6),


direction VARCHAR(10),


reference_type VARCHAR(64),


reference_id VARCHAR(64),


description VARCHAR(256),


created_time DATETIME

);
```

---

## transaction_type

第一版：

```
TOP_UP

充值


CONSUME

消费


REFUND

退款


ADJUST

人工调整


```

---

## direction

```
IN

收入


OUT

支出

```

---

例如：

充值：

```
IN

+100
```

AI消费：

```
OUT

-5

```

---

# Module 3：Balance Runtime

## 余额计算

MVP：

实时计算

```
Balance


=

SUM(transaction)

```

---

例如：

流水：

```
+100

-20

-10


```

余额：

```
70
```

---

后期：

增加：

```
balance_snapshot

余额快照

```

避免大量计算。

---

# 四、核心接口设计

## 1. 创建账户

POST

```
/api/billing/accounts
```

请求：

```json
{
"name":"echo个人账户",

"type":"PERSONAL"
}

```

返回：

```json
{
"id":10001
}
```

---

# 2. 查询余额

GET

```
/api/billing/accounts/{id}/balance

```

返回：

```json
{

"balance":100,

"currency":"CNY"

}

```

---

# 3. 创建交易

POST

```
/api/billing/transactions

```

请求：

```json
{

"accountId":10001,

"type":"CONSUME",

"amount":5,


"referenceType":"AI_REQUEST",

"referenceId":"req001"

}

```

---

返回：

```json
{

"transactionNo":

"TX202607160001"

}

```

---

# 4. 查询流水

GET

```
/api/billing/accounts/{id}/transactions

```

返回：

```
时间

类型

金额

来源

```

---

# 五、后台管理设计

## Billing Console

菜单：

```
商业中心


├── 账户管理

├── 交易流水

├── 余额查询

└── 手工调整

```

---

# 页面1：账户列表

## UX设计

顶部：

```
搜索

账户类型

状态

```

---

列表：

| 账户    | 类型 | 余额   | 状态 |
| ----- | -- | ---- | -- |
| Echo  | 个人 | 100  | 正常 |
| ABC公司 | 企业 | 5000 | 正常 |

---

点击：

进入账户详情。

---

# 页面2：账户详情

类似银行账户。

布局：

```
--------------------------------

账户信息


Echo

个人账户


余额

¥100


--------------------------------


交易流水


时间 | 类型 | 金额


--------------------------------

```

---

# 页面3：流水详情

重点：

审计视角。

显示：

```
交易编号:

TX20260716001


发生时间:

15:30


金额:

-5


来源:

AI_REQUEST


业务ID:

req001


```

---

# 六、用户端 UX

用户中心：

新增：

```
费用中心
```

---

## 页面：我的账户

设计：

黑金风格：

```
--------------------------------


账户余额


¥100.00


--------------------------------


最近消费


AI调用

-¥0.02


文件存储

-¥1


--------------------------------


交易记录

查看全部

```

---

# 七、异常处理设计

## 1. 重复扣费

场景：

网络重试。

例如：

AI请求：

第一次：

```
扣5元
```

第二次：

重复请求。

解决：

幂等。

transaction：

增加：

```
unique(reference_type,reference_id)

```

---

## 2. 金额精度

禁止：

float

必须：

```
decimal
```

例如：

```
0.000001

```

因为：

Token计费。

---

## 3. 负余额

MVP策略：

允许。

原因：

企业账期。

例如：

```
-100

欠费

```

---

但是：

记录：

```
balance_status

OVERDUE

```

---

# 八、安全设计

## 1. 权限

普通用户：

只能：

```
查看自己的账户
```

管理员：

```
查看组织账户
```

超级管理员：

```
调整余额
```

---

## 2. 手工调整必须原因

例如：

管理员增加：

100

必须填写：

```
原因:

测试补偿

```

生成：

```
ADJUST transaction

```

不能直接修改。

---

# 九、数据库设计总结

Phase0：

只需要：

```text
billing_account

billing_transaction

billing_balance_snapshot(预留)

billing_operation_log
```

---

# 十、和其他 Core 模块集成

## core-ai

未来：

```
AI调用

 |
产生usage

 |
billing transaction

```

---

## core-storage

```
文件上传

 |

存储计量

 |

billing

```

---

## core-notification

```
短信发送

 |

计费事件

 |

billing

```

---

# 十一、MVP开发顺序

建议：

## Week 1

基础工程：

```
core-billing

├── backend

├── frontend

├── admin

└── sdk

```

---

## Week 2

完成：

```
Account

Transaction

Balance

```

---

## Week 3

完成：

```
Console

权限

审计

```

---

## Week 4

接入：

```
core-ai测试扣费

```

---

# 十二、Phase 0 完成标准

达到：

✅ 用户拥有商业账户
✅ 可以充值模拟金额
✅ 可以消费扣减
✅ 所有交易可追溯
✅ 支持退款
✅ 支持管理员调整
✅ 支持未来接支付
✅ 不依赖 Redis/MQ
✅ SQLite 可运行

---

## 最终形态

Phase 0 做完以后：

`core-billing` 虽然还不能赚钱，但是已经具备商业系统的骨架：

```
钱从哪里来
      |
钱怎么变化
      |
为什么变化
      |
谁改变的
      |
什么时候发生
```

这就是后续订阅、支付、额度、AI计费全部建立的地基。
