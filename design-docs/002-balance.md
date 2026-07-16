# Phase 1：Account Balance Runtime 详细设计

```text
core-billing-account-balance
```

---

# 一、Phase 1 定位

## 核心目标

在 Phase 0 **账本基础能力** 之上，建立真正可用的：

> 用户资产账户 + 余额管理能力。

Phase 0 解决：

```
有没有交易记录？
```

Phase 1 解决：

```
用户现在还有多少钱？
能不能消费？
能不能冻结？
```

---

# 二、为什么 Phase 1 紧接 Account Balance？

商业系统演进：

```
Ledger（流水）
      |
      ↓
Balance（余额）
      |
      ↓
Quota（额度）
      |
      ↓
Subscription（订阅）
      |
      ↓
Payment（支付）
```

没有余额：

* AI无法收费
* API无法计费
* 存储无法扣费
* SaaS套餐无法运行

所以：

> Balance 是 Billing 从“记录系统”进入“交易系统”的关键一步。

---

# 三、整体架构

```text
                  core-platform


                      

                  core-billing


                      

        +--------------+--------------+

        |                             |

        ↓                             ↓


 Billing Account              Balance Runtime


        |                             |

        ↓                             ↓


 Ledger Transaction        Available Balance


```

---

# 四、核心设计原则

## 原则1：余额不是事实来源

非常重要。

错误：

```
balance = 100
```

然后修改：

```
balance = 80
```

正确：

```
Ledger

+100

-20


Balance

=80

```

Ledger 永远是真实来源。

Balance 是：

> 快速访问结果。

---

# 五、账户余额模型设计

## 1. Balance 类型

不要只有一个余额。

企业平台通常需要多个钱包。

例如：

```
Billing Account


    |
    |
    +---- Cash Balance
    |
    |
    +---- Credit Balance
    |
    |
    +---- Bonus Balance
    |
    |
    +---- Frozen Balance

```

---

## 类型说明

### Cash Balance

真实现金。

例如：

充值：

```
¥100
```

---

### Credit Balance

平台赠送额度。

例如：

注册赠送：

```
1000 Token
```

---

### Bonus Balance

优惠余额。

例如：

活动：

```
¥20
```

---

### Frozen Balance

冻结资金。

例如：

AI任务执行中：

```
冻结 ¥10
```

---

# 六、数据库设计

## 1. billing_balance

余额主表。

```sql
CREATE TABLE billing_balance
(
    id INTEGER PRIMARY KEY,


    account_id BIGINT,


    balance_type VARCHAR(32),


    amount DECIMAL(18,6),


    frozen_amount DECIMAL(18,6),


    currency VARCHAR(10),


    version INTEGER,


    created_time DATETIME,


    updated_time DATETIME

);
```

---

字段：

| 字段            | 说明   |
| ------------- | ---- |
| account_id    | 账户   |
| balance_type  | 余额类型 |
| amount        | 可用余额 |
| frozen_amount | 冻结金额 |
| version       | 乐观锁  |

---

## 2. 为什么需要 version？

防止并发扣款。

例如：

两个请求同时：

```
余额100
```

请求A：

```
扣80
```

请求B：

```
扣50
```

如果没有锁：

可能：

```
余额=-30
```

---

采用：

```
乐观锁
```

更新：

```sql
update billing_balance

set amount=20,

version=version+1

where version=10
```

---

# 七、余额状态机

账户余额：

```text
             正常

              |

              ↓

          余额不足

              |

              ↓

           欠费


```

---

状态：

```
NORMAL

LOW_BALANCE

OVERDUE

DISABLED

```

---

# 八、核心业务流程设计

# 场景1：充值

未来支付：

```
用户支付

↓

Payment

↓

Billing Transaction

↓

Balance增加

```

MVP模拟：

管理员充值。

流程：

```
Admin

 |

创建充值交易

 |

Ledger +100

 |

Balance +100

```

---

接口：

```
POST

/api/billing/accounts/{id}/deposit
```

请求：

```json
{
 amount:100,
 currency:"CNY"
}
```

---

---

# 场景2：消费扣款

例如：

AI调用。

流程：

```
AI Request


↓

Billing Check Balance


↓

Freeze


↓

Execute


↓

Confirm


↓

Ledger


↓

Balance


```

---

注意：

不能直接扣。

应该：

```
冻结
 ↓
确认
 ↓
扣除
```

---

为什么？

因为：

AI调用可能失败。

---

# 场景3：冻结余额

例如：

预计：

```
消耗 ¥10
```

请求：

```
freeze 10
```

余额：

之前：

```
100
```

之后：

```
available=90

frozen=10

```

---

数据库：

```
amount

100


frozen

10


```

---

# 场景4：确认扣款

执行成功：

```
frozen -10

amount -10

ledger -10

```

最终：

```
90
```

---

# 场景5：释放冻结

执行失败：

```
frozen -10


amount +10


```

余额恢复。

---

# 九、核心 API 设计

## 1. 查询余额

```
GET

/api/billing/accounts/{id}/balances

```

返回：

```json
[
{
"type":"CASH",

"available":100,

"frozen":0,

"currency":"CNY"
}
]

```

---

# 2. 冻结余额

```
POST

/api/billing/balance/freeze

```

请求：

```json
{

"accountId":1001,

"amount":10,

"referenceId":"ai-request-001"

}

```

---

返回：

```json
{

freezeId:"F20260701"

}

```

---

# 3. 确认扣款

```
POST

/api/billing/balance/consume

```

---

# 4. 释放冻结

```
POST

/api/billing/balance/release

```

---

# 十、管理后台 UX

## 菜单变化

```
商业中心


├── 账户管理

├── 余额管理 ⭐

├── 交易流水

└── 操作记录

```

---

# 页面1：余额总览

设计：

黑金风格。

```
--------------------------------


账户：

Echo


现金余额


¥1,000.00



冻结金额


¥50.00



可用余额


¥950.00


--------------------------------


```

---

# 页面2：余额流水

类似银行。

```
时间

类型

金额

余额变化


15:00

AI消费

-5

995


```

---

# 页面3：管理员调整余额

危险操作。

必须：

```
金额:

+100


原因:

客户补偿


审批:

管理员

```

---

生成：

```
ADJUST Transaction

```

---

# 十一、用户端 UX

用户中心：

新增：

```
账户余额
```

页面：

```
我的资产


¥100.00


现金余额


赠送额度


冻结金额



充值按钮


消费记录


```

---

# 十二、安全设计

## 1. 禁止直接修改余额

禁止：

```java
balance.setAmount()
```

必须：

```
BalanceService

       |

Ledger

       |

Balance Update

```

---

## 2. 所有操作幂等

例如：

支付回调重复：

```
payment_success
payment_success
```

只能产生一次余额增加。

---

方案：

```
unique(reference_id)
```

---

## 3. 金额精度

统一：

```
DECIMAL(18,6)
```

支持：

AI Token：

```
0.000001
```

---

# 十三、和其他 Core 模块集成

## core-ai

未来：

```
AI Request


↓

Balance Freeze


↓

AI Execute


↓

Consume


```

---

## core-storage

```
Upload File


↓

Storage Usage


↓

Balance Consume

```

---

## core-openapi

API调用：

```
API Request


↓

Quota Check


↓

Balance Check


↓

Consume

```

---

# 十四、MVP实现范围

必须实现：

## Backend

```
Balance Domain

Freeze

Consume

Release

Transaction Integration

```

---

## Frontend

用户：

```
余额页面

消费记录

```

Admin：

```
账户余额

人工调整

```

---

## 暂不实现

❌ 自动充值

❌ 银行支付

❌ 多币种兑换

❌ 信用额度

❌ 企业账期

---

# 十五、完成标准

Phase 1 完成后：

✅ 每个用户拥有商业账户
✅ 支持余额查询
✅ 支持充值模拟
✅ 支持消费扣款
✅ 支持冻结/释放
✅ 支持并发安全
✅ 支持未来支付接入
✅ 支持 AI/Storage/API 消费

---

# 最终能力变化

Phase 0：

```
我知道发生了多少钱
```

Phase 1：

```
我知道用户现在还有多少钱，
并且可以安全地使用它。
```

这一步完成后，`core-billing` 才真正成为整个 Core Platform 的**经济引擎**。
