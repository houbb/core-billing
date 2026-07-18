-- ============================================================
-- P1-P9 Enterprise Billing Platform
-- 约束：所有业务表包含审计五字段；只使用逻辑引用，不使用外键。
-- ============================================================

-- 补齐 P0 历史表的强制审计字段。
ALTER TABLE billing_transaction ADD COLUMN update_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00';
ALTER TABLE billing_transaction ADD COLUMN update_user VARCHAR(64) NOT NULL DEFAULT '';
UPDATE billing_transaction SET update_time = create_time, update_user = create_user
WHERE update_time = '1970-01-01 00:00:00';
DROP INDEX IF EXISTS idx_bt_idempotent;
CREATE UNIQUE INDEX IF NOT EXISTS idx_bt_idempotent
    ON billing_transaction(reference_type, reference_id)
    WHERE reference_type <> '' AND reference_id <> '';

ALTER TABLE billing_operation_log ADD COLUMN update_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00';
ALTER TABLE billing_operation_log ADD COLUMN create_user VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE billing_operation_log ADD COLUMN update_user VARCHAR(64) NOT NULL DEFAULT '';
UPDATE billing_operation_log
SET update_time = create_time, create_user = operator, update_user = operator
WHERE update_time = '1970-01-01 00:00:00';

ALTER TABLE billing_balance_snapshot ADD COLUMN update_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00';
ALTER TABLE billing_balance_snapshot ADD COLUMN create_user VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE billing_balance_snapshot ADD COLUMN update_user VARCHAR(64) NOT NULL DEFAULT '';
UPDATE billing_balance_snapshot SET update_time = create_time
WHERE update_time = '1970-01-01 00:00:00';

-- ============================================================
-- P1 Balance Runtime
-- ============================================================

-- 账户余额投影：amount 为可用余额，frozen_amount 为冻结余额。
CREATE TABLE IF NOT EXISTS billing_balance (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id     INTEGER NOT NULL,
    balance_type   VARCHAR(32) NOT NULL DEFAULT 'CASH',
    amount         DECIMAL(18,6) NOT NULL DEFAULT 0,
    frozen_amount  DECIMAL(18,6) NOT NULL DEFAULT 0,
    currency       VARCHAR(10) NOT NULL DEFAULT 'CNY',
    version        INTEGER NOT NULL DEFAULT 0,
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_balance_account_type_currency
    ON billing_balance(account_id, balance_type, currency);
CREATE INDEX IF NOT EXISTS idx_balance_account ON billing_balance(account_id);

-- 余额两阶段预留记录，reference_id 保证业务幂等。
CREATE TABLE IF NOT EXISTS billing_balance_reservation (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    reservation_no   VARCHAR(64) NOT NULL,
    account_id       INTEGER NOT NULL,
    balance_type     VARCHAR(32) NOT NULL DEFAULT 'CASH',
    amount           DECIMAL(18,6) NOT NULL,
    consumed_amount  DECIMAL(18,6) NOT NULL DEFAULT 0,
    currency         VARCHAR(10) NOT NULL DEFAULT 'CNY',
    reference_id     VARCHAR(128) NOT NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'RESERVED',
    description      VARCHAR(256) NOT NULL DEFAULT '',
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user      VARCHAR(64) NOT NULL DEFAULT '',
    update_user      VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_balance_reservation_no
    ON billing_balance_reservation(reservation_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_balance_reservation_reference
    ON billing_balance_reservation(reference_id);
CREATE INDEX IF NOT EXISTS idx_balance_reservation_account_status
    ON billing_balance_reservation(account_id, status);

-- ============================================================
-- P2 Pricing Runtime
-- ============================================================

-- 可收费资源定义。
CREATE TABLE IF NOT EXISTS billing_resource (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    resource_code  VARCHAR(64) NOT NULL,
    resource_name  VARCHAR(128) NOT NULL,
    unit           VARCHAR(32) NOT NULL,
    status         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    description    VARCHAR(256) NOT NULL DEFAULT '',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_resource_code ON billing_resource(resource_code);
CREATE INDEX IF NOT EXISTS idx_resource_status ON billing_resource(status);

-- 价格规则，pricing_mode 支持 FIXED / UNIT / TIERED。
CREATE TABLE IF NOT EXISTS billing_price_rule (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    resource_code   VARCHAR(64) NOT NULL,
    rule_name       VARCHAR(128) NOT NULL,
    pricing_mode    VARCHAR(32) NOT NULL DEFAULT 'UNIT',
    unit_quantity   DECIMAL(18,6) NOT NULL DEFAULT 1,
    condition_json  TEXT NOT NULL DEFAULT '{}',
    tier_json       TEXT NOT NULL DEFAULT '[]',
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user     VARCHAR(64) NOT NULL DEFAULT '',
    update_user     VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_price_rule_resource_status
    ON billing_price_rule(resource_code, status);
CREATE INDEX IF NOT EXISTS idx_price_rule_name ON billing_price_rule(rule_name);

-- 不可覆盖的价格版本。
CREATE TABLE IF NOT EXISTS billing_price_version (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_id         INTEGER NOT NULL,
    version_no      INTEGER NOT NULL,
    price           DECIMAL(18,6) NOT NULL,
    effective_time  DATETIME NOT NULL,
    expire_time     DATETIME,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user     VARCHAR(64) NOT NULL DEFAULT '',
    update_user     VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_price_version_rule_no
    ON billing_price_version(rule_id, version_no);
CREATE INDEX IF NOT EXISTS idx_price_version_effective
    ON billing_price_version(rule_id, effective_time, expire_time);

-- ============================================================
-- P3 Metering Runtime
-- ============================================================

-- 计量器定义。
CREATE TABLE IF NOT EXISTS billing_meter_definition (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    resource_code     VARCHAR(64) NOT NULL,
    meter_name        VARCHAR(128) NOT NULL,
    unit              VARCHAR(32) NOT NULL,
    aggregation_type  VARCHAR(32) NOT NULL DEFAULT 'SUM',
    status            VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user       VARCHAR(64) NOT NULL DEFAULT '',
    update_user       VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_meter_resource ON billing_meter_definition(resource_code);
CREATE INDEX IF NOT EXISTS idx_meter_status ON billing_meter_definition(status);

-- 原始用量事实，只追加不修改。
CREATE TABLE IF NOT EXISTS billing_usage_event (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id       VARCHAR(128) NOT NULL,
    tenant_id      VARCHAR(64) NOT NULL,
    account_id     INTEGER,
    resource_code  VARCHAR(64) NOT NULL,
    quantity       DECIMAL(18,6) NOT NULL,
    unit           VARCHAR(32) NOT NULL,
    metadata       TEXT NOT NULL DEFAULT '{}',
    event_time     DATETIME NOT NULL,
    status         VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    cost           DECIMAL(18,6) NOT NULL DEFAULT 0,
    currency       VARCHAR(10) NOT NULL DEFAULT 'CNY',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_usage_event_id ON billing_usage_event(event_id);
CREATE INDEX IF NOT EXISTS idx_usage_event_tenant_time
    ON billing_usage_event(tenant_id, event_time);
CREATE INDEX IF NOT EXISTS idx_usage_event_resource_time
    ON billing_usage_event(resource_code, event_time);

-- 可计费使用记录。
CREATE TABLE IF NOT EXISTS billing_usage_record (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id       VARCHAR(128) NOT NULL,
    tenant_id      VARCHAR(64) NOT NULL,
    account_id     INTEGER,
    resource_code  VARCHAR(64) NOT NULL,
    quantity       DECIMAL(18,6) NOT NULL,
    unit           VARCHAR(32) NOT NULL,
    period         VARCHAR(16) NOT NULL,
    cost           DECIMAL(18,6) NOT NULL DEFAULT 0,
    currency       VARCHAR(10) NOT NULL DEFAULT 'CNY',
    status         VARCHAR(32) NOT NULL DEFAULT 'CALCULATED',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_usage_record_event ON billing_usage_record(event_id);
CREATE INDEX IF NOT EXISTS idx_usage_record_tenant_period
    ON billing_usage_record(tenant_id, period);
CREATE INDEX IF NOT EXISTS idx_usage_record_resource_period
    ON billing_usage_record(resource_code, period);

-- 每日聚合快照。
CREATE TABLE IF NOT EXISTS billing_usage_daily (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id      VARCHAR(64) NOT NULL,
    resource_code  VARCHAR(64) NOT NULL,
    usage_date     VARCHAR(10) NOT NULL,
    quantity       DECIMAL(18,6) NOT NULL DEFAULT 0,
    cost           DECIMAL(18,6) NOT NULL DEFAULT 0,
    currency       VARCHAR(10) NOT NULL DEFAULT 'CNY',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_usage_daily
    ON billing_usage_daily(tenant_id, resource_code, usage_date);
CREATE INDEX IF NOT EXISTS idx_usage_daily_date ON billing_usage_daily(usage_date);

-- ============================================================
-- P4 Quota Runtime
-- ============================================================

-- 额度定义。
CREATE TABLE IF NOT EXISTS billing_quota_definition (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    resource_code      VARCHAR(64) NOT NULL,
    quota_name         VARCHAR(128) NOT NULL,
    unit               VARCHAR(32) NOT NULL,
    period             VARCHAR(32) NOT NULL DEFAULT 'MONTH',
    warning_threshold  DECIMAL(5,2) NOT NULL DEFAULT 80,
    status             VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user        VARCHAR(64) NOT NULL DEFAULT '',
    update_user        VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_quota_definition_resource
    ON billing_quota_definition(resource_code);
CREATE INDEX IF NOT EXISTS idx_quota_definition_status
    ON billing_quota_definition(status);

-- 租户周期额度，version 用于乐观并发控制。
CREATE TABLE IF NOT EXISTS billing_quota_allocation (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id       VARCHAR(64) NOT NULL,
    resource_code   VARCHAR(64) NOT NULL,
    quota_total     DECIMAL(18,6) NOT NULL,
    quota_used      DECIMAL(18,6) NOT NULL DEFAULT 0,
    quota_reserved  DECIMAL(18,6) NOT NULL DEFAULT 0,
    period_start    VARCHAR(10) NOT NULL,
    period_end      VARCHAR(10) NOT NULL,
    policy          VARCHAR(32) NOT NULL DEFAULT 'BLOCK',
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version         INTEGER NOT NULL DEFAULT 0,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user     VARCHAR(64) NOT NULL DEFAULT '',
    update_user     VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_quota_allocation_period
    ON billing_quota_allocation(tenant_id, resource_code, period_start);
CREATE INDEX IF NOT EXISTS idx_quota_allocation_active
    ON billing_quota_allocation(tenant_id, resource_code, status, period_end);

-- 额度两阶段预留。
CREATE TABLE IF NOT EXISTS billing_quota_reservation (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    reservation_no    VARCHAR(64) NOT NULL,
    allocation_id     INTEGER NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    resource_code     VARCHAR(64) NOT NULL,
    amount            DECIMAL(18,6) NOT NULL,
    committed_amount  DECIMAL(18,6) NOT NULL DEFAULT 0,
    reference_id      VARCHAR(128) NOT NULL,
    status            VARCHAR(32) NOT NULL DEFAULT 'RESERVED',
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user       VARCHAR(64) NOT NULL DEFAULT '',
    update_user       VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_quota_reservation_no
    ON billing_quota_reservation(reservation_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_quota_reservation_reference
    ON billing_quota_reservation(reference_id);
CREATE INDEX IF NOT EXISTS idx_quota_reservation_allocation_status
    ON billing_quota_reservation(allocation_id, status);

-- 额度阈值提醒。
CREATE TABLE IF NOT EXISTS billing_quota_alert (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id      VARCHAR(64) NOT NULL,
    resource_code  VARCHAR(64) NOT NULL,
    alert_type     VARCHAR(32) NOT NULL,
    threshold      DECIMAL(5,2) NOT NULL,
    current_usage  DECIMAL(18,6) NOT NULL,
    status         VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_quota_alert_tenant_status
    ON billing_quota_alert(tenant_id, status);

-- ============================================================
-- P5 Subscription Runtime
-- ============================================================

-- 商业产品。
CREATE TABLE IF NOT EXISTS billing_product (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    product_code   VARCHAR(64) NOT NULL,
    product_name   VARCHAR(128) NOT NULL,
    description    VARCHAR(512) NOT NULL DEFAULT '',
    status         VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_product_code ON billing_product(product_code);
CREATE INDEX IF NOT EXISTS idx_product_status ON billing_product(status);

-- 套餐主表。
CREATE TABLE IF NOT EXISTS billing_plan (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id       INTEGER NOT NULL,
    plan_code        VARCHAR(64) NOT NULL,
    plan_name        VARCHAR(128) NOT NULL,
    billing_cycle    VARCHAR(32) NOT NULL DEFAULT 'MONTHLY',
    price            DECIMAL(18,6) NOT NULL DEFAULT 0,
    currency         VARCHAR(10) NOT NULL DEFAULT 'CNY',
    status           VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    current_version  INTEGER NOT NULL DEFAULT 1,
    trial_days       INTEGER NOT NULL DEFAULT 0,
    description      VARCHAR(512) NOT NULL DEFAULT '',
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user      VARCHAR(64) NOT NULL DEFAULT '',
    update_user      VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_plan_code ON billing_plan(plan_code);
CREATE INDEX IF NOT EXISTS idx_plan_product_status ON billing_plan(product_id, status);

-- 套餐权益。
CREATE TABLE IF NOT EXISTS billing_plan_item (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id        INTEGER NOT NULL,
    item_type      VARCHAR(32) NOT NULL,
    resource_code  VARCHAR(64) NOT NULL DEFAULT '',
    item_code      VARCHAR(64) NOT NULL DEFAULT '',
    item_value     DECIMAL(18,6) NOT NULL DEFAULT 0,
    unit           VARCHAR(32) NOT NULL DEFAULT '',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_plan_item_plan_type ON billing_plan_item(plan_id, item_type);
CREATE INDEX IF NOT EXISTS idx_plan_item_resource ON billing_plan_item(resource_code);

-- 套餐历史版本快照。
CREATE TABLE IF NOT EXISTS billing_plan_version (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id         INTEGER NOT NULL,
    version_no      INTEGER NOT NULL,
    snapshot_json   TEXT NOT NULL,
    effective_time  DATETIME NOT NULL,
    expire_time     DATETIME,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user     VARCHAR(64) NOT NULL DEFAULT '',
    update_user     VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_plan_version_no ON billing_plan_version(plan_id, version_no);
CREATE INDEX IF NOT EXISTS idx_plan_version_effective ON billing_plan_version(plan_id, effective_time);

-- 用户/租户订阅状态机。
CREATE TABLE IF NOT EXISTS billing_subscription (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id              VARCHAR(64) NOT NULL,
    plan_id                INTEGER NOT NULL,
    plan_version           INTEGER NOT NULL,
    previous_plan_id       INTEGER,
    status                 VARCHAR(32) NOT NULL,
    start_time             DATETIME NOT NULL,
    end_time               DATETIME,
    next_billing_time      DATETIME,
    trial_end_time         DATETIME,
    cancel_at_period_end   INTEGER NOT NULL DEFAULT 0,
    create_time            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user            VARCHAR(64) NOT NULL DEFAULT '',
    update_user            VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_subscription_tenant_status
    ON billing_subscription(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_subscription_plan
    ON billing_subscription(plan_id, status);

-- ============================================================
-- P6 Payment Runtime
-- ============================================================

-- 支付渠道配置只保存非敏感引用。
CREATE TABLE IF NOT EXISTS billing_payment_channel (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_code   VARCHAR(64) NOT NULL,
    channel_name   VARCHAR(128) NOT NULL,
    driver_code    VARCHAR(64) NOT NULL,
    status         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    config_ref     VARCHAR(256) NOT NULL DEFAULT '',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_channel_code
    ON billing_payment_channel(channel_code);
CREATE INDEX IF NOT EXISTS idx_payment_channel_status
    ON billing_payment_channel(status);

-- 统一支付订单。
CREATE TABLE IF NOT EXISTS billing_payment_order (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    order_no           VARCHAR(64) NOT NULL,
    tenant_id          VARCHAR(64) NOT NULL,
    business_type      VARCHAR(32) NOT NULL,
    business_id        VARCHAR(128) NOT NULL,
    account_id         INTEGER,
    plan_id            INTEGER,
    amount             DECIMAL(18,6) NOT NULL,
    currency           VARCHAR(10) NOT NULL DEFAULT 'CNY',
    status             VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    channel_code       VARCHAR(64) NOT NULL,
    idempotency_key    VARCHAR(128) NOT NULL,
    provider_trade_no  VARCHAR(128) NOT NULL DEFAULT '',
    paid_time          DATETIME,
    failure_reason     VARCHAR(256) NOT NULL DEFAULT '',
    create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user        VARCHAR(64) NOT NULL DEFAULT '',
    update_user        VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_order_no ON billing_payment_order(order_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_order_idempotency
    ON billing_payment_order(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_payment_order_tenant_status
    ON billing_payment_order(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_payment_order_business
    ON billing_payment_order(business_type, business_id);

-- 支付回调事实和验签结果。
CREATE TABLE IF NOT EXISTS billing_payment_callback (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    callback_id     VARCHAR(128) NOT NULL,
    order_no        VARCHAR(64) NOT NULL,
    channel_code    VARCHAR(64) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    amount          DECIMAL(18,6) NOT NULL,
    payload         TEXT NOT NULL DEFAULT '{}',
    signature       VARCHAR(256) NOT NULL DEFAULT '',
    status          VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    processed_time  DATETIME,
    error_message   VARCHAR(256) NOT NULL DEFAULT '',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user     VARCHAR(64) NOT NULL DEFAULT '',
    update_user     VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_callback_id
    ON billing_payment_callback(callback_id);
CREATE INDEX IF NOT EXISTS idx_payment_callback_order
    ON billing_payment_callback(order_no, status);

-- 退款记录。
CREATE TABLE IF NOT EXISTS billing_refund (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    refund_no           VARCHAR(64) NOT NULL,
    payment_order_id    INTEGER NOT NULL,
    amount              DECIMAL(18,6) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    reason              VARCHAR(256) NOT NULL,
    provider_refund_no  VARCHAR(128) NOT NULL DEFAULT '',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user         VARCHAR(64) NOT NULL DEFAULT '',
    update_user         VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_refund_no ON billing_refund(refund_no);
CREATE INDEX IF NOT EXISTS idx_refund_order_status ON billing_refund(payment_order_id, status);

-- 对账结果。
CREATE TABLE IF NOT EXISTS billing_reconciliation (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    reconcile_date  VARCHAR(10) NOT NULL,
    order_no        VARCHAR(64) NOT NULL,
    local_amount    DECIMAL(18,6) NOT NULL,
    channel_amount  DECIMAL(18,6) NOT NULL,
    result          VARCHAR(32) NOT NULL,
    detail          VARCHAR(512) NOT NULL DEFAULT '',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user     VARCHAR(64) NOT NULL DEFAULT '',
    update_user     VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_reconciliation_date_result
    ON billing_reconciliation(reconcile_date, result);
CREATE INDEX IF NOT EXISTS idx_reconciliation_order ON billing_reconciliation(order_no);

-- 支付操作审计。
CREATE TABLE IF NOT EXISTS billing_payment_log (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    order_no     VARCHAR(64) NOT NULL,
    operation    VARCHAR(64) NOT NULL,
    detail       TEXT NOT NULL DEFAULT '',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user  VARCHAR(64) NOT NULL DEFAULT '',
    update_user  VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_payment_log_order_time
    ON billing_payment_log(order_no, create_time);

-- ============================================================
-- P7 Invoice Runtime
-- ============================================================

-- 周期账单，生成后不允许覆盖。
CREATE TABLE IF NOT EXISTS billing_invoice (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_no       VARCHAR(64) NOT NULL,
    tenant_id        VARCHAR(64) NOT NULL,
    billing_period   VARCHAR(16) NOT NULL,
    currency         VARCHAR(10) NOT NULL DEFAULT 'CNY',
    subtotal         DECIMAL(18,6) NOT NULL DEFAULT 0,
    tax              DECIMAL(18,6) NOT NULL DEFAULT 0,
    discount         DECIMAL(18,6) NOT NULL DEFAULT 0,
    total            DECIMAL(18,6) NOT NULL DEFAULT 0,
    status           VARCHAR(32) NOT NULL DEFAULT 'GENERATED',
    due_time         DATETIME,
    generated_time   DATETIME NOT NULL,
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user      VARCHAR(64) NOT NULL DEFAULT '',
    update_user      VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_invoice_no ON billing_invoice(invoice_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_invoice_tenant_period
    ON billing_invoice(tenant_id, billing_period);
CREATE INDEX IF NOT EXISTS idx_invoice_status_due ON billing_invoice(status, due_time);

-- 账单明细。
CREATE TABLE IF NOT EXISTS billing_invoice_item (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_id     INTEGER NOT NULL,
    item_type      VARCHAR(32) NOT NULL DEFAULT 'USAGE',
    resource_code  VARCHAR(64) NOT NULL DEFAULT '',
    description    VARCHAR(256) NOT NULL,
    quantity       DECIMAL(18,6) NOT NULL,
    unit_price     DECIMAL(18,6) NOT NULL,
    amount         DECIMAL(18,6) NOT NULL,
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_invoice_item_invoice ON billing_invoice_item(invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoice_item_resource ON billing_invoice_item(resource_code);

-- 账户对账单。
CREATE TABLE IF NOT EXISTS billing_statement (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id        VARCHAR(64) NOT NULL,
    period           VARCHAR(16) NOT NULL,
    opening_balance  DECIMAL(18,6) NOT NULL DEFAULT 0,
    closing_balance  DECIMAL(18,6) NOT NULL DEFAULT 0,
    total_in         DECIMAL(18,6) NOT NULL DEFAULT 0,
    total_out        DECIMAL(18,6) NOT NULL DEFAULT 0,
    currency         VARCHAR(10) NOT NULL DEFAULT 'CNY',
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user      VARCHAR(64) NOT NULL DEFAULT '',
    update_user      VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_statement_tenant_period
    ON billing_statement(tenant_id, period);
CREATE INDEX IF NOT EXISTS idx_statement_period ON billing_statement(period);

-- 结算记录。
CREATE TABLE IF NOT EXISTS billing_settlement (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    settlement_no     VARCHAR(64) NOT NULL,
    invoice_id        INTEGER NOT NULL,
    payment_order_id  INTEGER,
    amount            DECIMAL(18,6) NOT NULL,
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    settled_time      DATETIME,
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user       VARCHAR(64) NOT NULL DEFAULT '',
    update_user       VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_settlement_no ON billing_settlement(settlement_no);
CREATE INDEX IF NOT EXISTS idx_settlement_invoice_status
    ON billing_settlement(invoice_id, status);

-- 贷项通知单，替代修改/删除历史 Invoice。
CREATE TABLE IF NOT EXISTS billing_credit_note (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    credit_no    VARCHAR(64) NOT NULL,
    invoice_id   INTEGER NOT NULL,
    amount       DECIMAL(18,6) NOT NULL,
    reason       VARCHAR(256) NOT NULL,
    status       VARCHAR(32) NOT NULL DEFAULT 'ISSUED',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user  VARCHAR(64) NOT NULL DEFAULT '',
    update_user  VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_credit_note_no ON billing_credit_note(credit_no);
CREATE INDEX IF NOT EXISTS idx_credit_note_invoice ON billing_credit_note(invoice_id, status);

-- 固定税率规则。
CREATE TABLE IF NOT EXISTS billing_tax_rule (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    country      VARCHAR(32) NOT NULL,
    tax_type     VARCHAR(32) NOT NULL,
    rate         DECIMAL(8,6) NOT NULL,
    status       VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user  VARCHAR(64) NOT NULL DEFAULT '',
    update_user  VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tax_rule_country_type
    ON billing_tax_rule(country, tax_type);
CREATE INDEX IF NOT EXISTS idx_tax_rule_status ON billing_tax_rule(status);

-- ============================================================
-- P8 Revenue & Finance Runtime
-- ============================================================

-- 收入快照。
CREATE TABLE IF NOT EXISTS billing_revenue_snapshot (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    snapshot_date     VARCHAR(10) NOT NULL,
    period_type       VARCHAR(16) NOT NULL DEFAULT 'DAY',
    currency          VARCHAR(10) NOT NULL DEFAULT 'CNY',
    gross_revenue     DECIMAL(18,6) NOT NULL DEFAULT 0,
    net_revenue       DECIMAL(18,6) NOT NULL DEFAULT 0,
    refund_amount     DECIMAL(18,6) NOT NULL DEFAULT 0,
    outstanding       DECIMAL(18,6) NOT NULL DEFAULT 0,
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user       VARCHAR(64) NOT NULL DEFAULT '',
    update_user       VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_revenue_snapshot
    ON billing_revenue_snapshot(snapshot_date, period_type, currency);
CREATE INDEX IF NOT EXISTS idx_revenue_snapshot_date ON billing_revenue_snapshot(snapshot_date);

-- 供应商/资源成本事实。
CREATE TABLE IF NOT EXISTS billing_cost_record (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT '',
    resource_code   VARCHAR(64) NOT NULL,
    provider        VARCHAR(64) NOT NULL,
    cost            DECIMAL(18,6) NOT NULL,
    currency        VARCHAR(10) NOT NULL DEFAULT 'CNY',
    record_date     VARCHAR(10) NOT NULL,
    reference_type  VARCHAR(64) NOT NULL DEFAULT '',
    reference_id    VARCHAR(128) NOT NULL DEFAULT '',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user     VARCHAR(64) NOT NULL DEFAULT '',
    update_user     VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_cost_record_date_provider
    ON billing_cost_record(record_date, provider);
CREATE INDEX IF NOT EXISTS idx_cost_record_resource
    ON billing_cost_record(resource_code, record_date);

-- 利润快照。
CREATE TABLE IF NOT EXISTS billing_profit_snapshot (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    snapshot_date  VARCHAR(10) NOT NULL,
    period_type    VARCHAR(16) NOT NULL DEFAULT 'DAY',
    currency       VARCHAR(10) NOT NULL DEFAULT 'CNY',
    revenue        DECIMAL(18,6) NOT NULL DEFAULT 0,
    cost           DECIMAL(18,6) NOT NULL DEFAULT 0,
    profit         DECIMAL(18,6) NOT NULL DEFAULT 0,
    margin         DECIMAL(12,6) NOT NULL DEFAULT 0,
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_profit_snapshot
    ON billing_profit_snapshot(snapshot_date, period_type, currency);
CREATE INDEX IF NOT EXISTS idx_profit_snapshot_date ON billing_profit_snapshot(snapshot_date);

-- 通用 KPI 快照。
CREATE TABLE IF NOT EXISTS billing_kpi_snapshot (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    snapshot_date    VARCHAR(10) NOT NULL,
    period_type      VARCHAR(16) NOT NULL DEFAULT 'DAY',
    kpi_code         VARCHAR(64) NOT NULL,
    value            DECIMAL(24,6) NOT NULL,
    dimension_type   VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
    dimension_value  VARCHAR(128) NOT NULL DEFAULT '',
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user      VARCHAR(64) NOT NULL DEFAULT '',
    update_user      VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_kpi_snapshot
    ON billing_kpi_snapshot(snapshot_date, period_type, kpi_code, dimension_type, dimension_value);
CREATE INDEX IF NOT EXISTS idx_kpi_code_date ON billing_kpi_snapshot(kpi_code, snapshot_date);

-- 线性预测结果。
CREATE TABLE IF NOT EXISTS billing_forecast (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    forecast_period    VARCHAR(16) NOT NULL,
    currency           VARCHAR(10) NOT NULL DEFAULT 'CNY',
    predicted_revenue  DECIMAL(18,6) NOT NULL,
    method             VARCHAR(32) NOT NULL DEFAULT 'LINEAR',
    basis_json         TEXT NOT NULL DEFAULT '{}',
    create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user        VARCHAR(64) NOT NULL DEFAULT '',
    update_user        VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_forecast_period_currency
    ON billing_forecast(forecast_period, currency);
CREATE INDEX IF NOT EXISTS idx_forecast_period ON billing_forecast(forecast_period);

-- 客户经营指标。
CREATE TABLE IF NOT EXISTS billing_customer_metrics (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    snapshot_date  VARCHAR(10) NOT NULL,
    tenant_id      VARCHAR(64) NOT NULL,
    mrr            DECIMAL(18,6) NOT NULL DEFAULT 0,
    revenue        DECIMAL(18,6) NOT NULL DEFAULT 0,
    cost           DECIMAL(18,6) NOT NULL DEFAULT 0,
    profit         DECIMAL(18,6) NOT NULL DEFAULT 0,
    churn_risk     DECIMAL(8,6) NOT NULL DEFAULT 0,
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_metrics
    ON billing_customer_metrics(snapshot_date, tenant_id);
CREATE INDEX IF NOT EXISTS idx_customer_metrics_tenant
    ON billing_customer_metrics(tenant_id, snapshot_date);

-- 产品经营指标。
CREATE TABLE IF NOT EXISTS billing_product_metrics (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    snapshot_date       VARCHAR(10) NOT NULL,
    product_code        VARCHAR(64) NOT NULL,
    revenue             DECIMAL(18,6) NOT NULL DEFAULT 0,
    cost                DECIMAL(18,6) NOT NULL DEFAULT 0,
    subscription_count  INTEGER NOT NULL DEFAULT 0,
    conversion_rate     DECIMAL(8,6) NOT NULL DEFAULT 0,
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user         VARCHAR(64) NOT NULL DEFAULT '',
    update_user         VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_product_metrics
    ON billing_product_metrics(snapshot_date, product_code);
CREATE INDEX IF NOT EXISTS idx_product_metrics_product
    ON billing_product_metrics(product_code, snapshot_date);

-- ============================================================
-- P9 Enterprise Billing Platform
-- ============================================================

-- 企业合同。
CREATE TABLE IF NOT EXISTS billing_contract (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    contract_no       VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    customer          VARCHAR(128) NOT NULL,
    plan_id           INTEGER,
    start_time        DATETIME NOT NULL,
    end_time          DATETIME NOT NULL,
    amount            DECIMAL(18,6) NOT NULL,
    currency          VARCHAR(10) NOT NULL DEFAULT 'CNY',
    payment_term      VARCHAR(32) NOT NULL DEFAULT 'NET30',
    status            VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    external_sign_id  VARCHAR(128) NOT NULL DEFAULT '',
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user       VARCHAR(64) NOT NULL DEFAULT '',
    update_user       VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_contract_no ON billing_contract(contract_no);
CREATE INDEX IF NOT EXISTS idx_contract_tenant_status ON billing_contract(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_contract_end_time ON billing_contract(end_time);

-- 多组织层级节点。
CREATE TABLE IF NOT EXISTS billing_organization_node (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id    VARCHAR(64) NOT NULL,
    node_code    VARCHAR(64) NOT NULL,
    parent_code  VARCHAR(64) NOT NULL DEFAULT '',
    node_type    VARCHAR(32) NOT NULL,
    node_name    VARCHAR(128) NOT NULL,
    path         VARCHAR(512) NOT NULL,
    status       VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user  VARCHAR(64) NOT NULL DEFAULT '',
    update_user  VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_org_node_code
    ON billing_organization_node(tenant_id, node_code);
CREATE INDEX IF NOT EXISTS idx_org_node_parent ON billing_organization_node(tenant_id, parent_code);

-- 多币种汇率版本。
CREATE TABLE IF NOT EXISTS billing_currency_rate (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    from_currency  VARCHAR(10) NOT NULL,
    to_currency    VARCHAR(10) NOT NULL,
    rate           DECIMAL(24,10) NOT NULL,
    effective_time DATETIME NOT NULL,
    status         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_currency_rate_version
    ON billing_currency_rate(from_currency, to_currency, effective_time);
CREATE INDEX IF NOT EXISTS idx_currency_rate_active
    ON billing_currency_rate(from_currency, to_currency, status, effective_time);

-- 营销活动。
CREATE TABLE IF NOT EXISTS billing_campaign (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    campaign_code VARCHAR(64) NOT NULL,
    campaign_name VARCHAR(128) NOT NULL,
    campaign_type VARCHAR(32) NOT NULL,
    start_time   DATETIME NOT NULL,
    end_time     DATETIME NOT NULL,
    status       VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    rules_json   TEXT NOT NULL DEFAULT '{}',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user  VARCHAR(64) NOT NULL DEFAULT '',
    update_user  VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_campaign_code ON billing_campaign(campaign_code);
CREATE INDEX IF NOT EXISTS idx_campaign_status_time ON billing_campaign(status, start_time, end_time);

-- 优惠券。
CREATE TABLE IF NOT EXISTS billing_coupon (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    coupon_code     VARCHAR(64) NOT NULL,
    campaign_id     INTEGER,
    discount_type   VARCHAR(32) NOT NULL,
    discount_value  DECIMAL(18,6) NOT NULL,
    usage_limit     INTEGER NOT NULL DEFAULT 1,
    used_count      INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    expire_time     DATETIME,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user     VARCHAR(64) NOT NULL DEFAULT '',
    update_user     VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_coupon_code ON billing_coupon(coupon_code);
CREATE INDEX IF NOT EXISTS idx_coupon_campaign_status ON billing_coupon(campaign_id, status);

-- 已应用折扣事实。
CREATE TABLE IF NOT EXISTS billing_discount (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    reference_type   VARCHAR(64) NOT NULL,
    reference_id     VARCHAR(128) NOT NULL,
    coupon_id        INTEGER,
    original_amount  DECIMAL(18,6) NOT NULL,
    discount_amount  DECIMAL(18,6) NOT NULL,
    final_amount     DECIMAL(18,6) NOT NULL,
    currency         VARCHAR(10) NOT NULL DEFAULT 'CNY',
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user      VARCHAR(64) NOT NULL DEFAULT '',
    update_user      VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_discount_reference
    ON billing_discount(reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_discount_coupon ON billing_discount(coupon_id);

-- Marketplace 商品。
CREATE TABLE IF NOT EXISTS billing_marketplace_listing (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    listing_code   VARCHAR(64) NOT NULL,
    creator_id     VARCHAR(64) NOT NULL,
    listing_name   VARCHAR(128) NOT NULL,
    listing_type   VARCHAR(32) NOT NULL,
    price          DECIMAL(18,6) NOT NULL,
    currency       VARCHAR(10) NOT NULL DEFAULT 'CNY',
    platform_rate  DECIMAL(8,6) NOT NULL DEFAULT 0.20,
    status         VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_listing_code ON billing_marketplace_listing(listing_code);
CREATE INDEX IF NOT EXISTS idx_listing_creator_status
    ON billing_marketplace_listing(creator_id, status);

-- Marketplace 订单。
CREATE TABLE IF NOT EXISTS billing_marketplace_order (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    order_no          VARCHAR(64) NOT NULL,
    listing_id        INTEGER NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    buyer_id          VARCHAR(64) NOT NULL,
    amount            DECIMAL(18,6) NOT NULL,
    currency          VARCHAR(10) NOT NULL DEFAULT 'CNY',
    status            VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    payment_order_id  INTEGER,
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user       VARCHAR(64) NOT NULL DEFAULT '',
    update_user       VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_marketplace_order_no
    ON billing_marketplace_order(order_no);
CREATE INDEX IF NOT EXISTS idx_marketplace_order_tenant_status
    ON billing_marketplace_order(tenant_id, status);

-- 渠道伙伴。
CREATE TABLE IF NOT EXISTS billing_partner (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    partner_code     VARCHAR(64) NOT NULL,
    partner_name     VARCHAR(128) NOT NULL,
    partner_type     VARCHAR(32) NOT NULL,
    commission_rate  DECIMAL(8,6) NOT NULL DEFAULT 0,
    status           VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user      VARCHAR(64) NOT NULL DEFAULT '',
    update_user      VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_partner_code ON billing_partner(partner_code);
CREATE INDEX IF NOT EXISTS idx_partner_type_status ON billing_partner(partner_type, status);

-- 伙伴订单归因。
CREATE TABLE IF NOT EXISTS billing_partner_order (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    order_no       VARCHAR(64) NOT NULL,
    partner_id     INTEGER NOT NULL,
    tenant_id      VARCHAR(64) NOT NULL,
    business_type  VARCHAR(32) NOT NULL,
    business_id    VARCHAR(128) NOT NULL,
    amount         DECIMAL(18,6) NOT NULL,
    currency       VARCHAR(10) NOT NULL DEFAULT 'CNY',
    status         VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user    VARCHAR(64) NOT NULL DEFAULT '',
    update_user    VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_partner_order_no ON billing_partner_order(order_no);
CREATE INDEX IF NOT EXISTS idx_partner_order_partner_status
    ON billing_partner_order(partner_id, status);

-- 伙伴佣金。
CREATE TABLE IF NOT EXISTS billing_commission (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    commission_no     VARCHAR(64) NOT NULL,
    partner_id        INTEGER NOT NULL,
    partner_order_id  INTEGER NOT NULL,
    amount            DECIMAL(18,6) NOT NULL,
    currency          VARCHAR(10) NOT NULL DEFAULT 'CNY',
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user       VARCHAR(64) NOT NULL DEFAULT '',
    update_user       VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_commission_no ON billing_commission(commission_no);
CREATE INDEX IF NOT EXISTS idx_commission_partner_status
    ON billing_commission(partner_id, status);

-- 企业预算。
CREATE TABLE IF NOT EXISTS billing_budget (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id          VARCHAR(64) NOT NULL,
    scope_type         VARCHAR(32) NOT NULL,
    scope_id           VARCHAR(64) NOT NULL,
    resource_code      VARCHAR(64) NOT NULL DEFAULT '',
    period             VARCHAR(16) NOT NULL,
    budget_amount      DECIMAL(18,6) NOT NULL,
    used_amount        DECIMAL(18,6) NOT NULL DEFAULT 0,
    currency           VARCHAR(10) NOT NULL DEFAULT 'CNY',
    warning_threshold  DECIMAL(5,2) NOT NULL DEFAULT 80,
    policy             VARCHAR(32) NOT NULL DEFAULT 'ALERT',
    status             VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version            INTEGER NOT NULL DEFAULT 0,
    create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user        VARCHAR(64) NOT NULL DEFAULT '',
    update_user        VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_budget_scope_period
    ON billing_budget(tenant_id, scope_type, scope_id, resource_code, period);
CREATE INDEX IF NOT EXISTS idx_budget_tenant_status ON billing_budget(tenant_id, status);

-- 预算提醒。
CREATE TABLE IF NOT EXISTS billing_budget_alert (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    budget_id    INTEGER NOT NULL,
    alert_type   VARCHAR(32) NOT NULL,
    threshold    DECIMAL(5,2) NOT NULL,
    used_amount  DECIMAL(18,6) NOT NULL,
    status       VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user  VARCHAR(64) NOT NULL DEFAULT '',
    update_user  VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_budget_alert_budget_status
    ON billing_budget_alert(budget_id, status);

-- 成本中心。
CREATE TABLE IF NOT EXISTS billing_cost_center (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id    VARCHAR(64) NOT NULL,
    center_code  VARCHAR(64) NOT NULL,
    center_name  VARCHAR(128) NOT NULL,
    parent_code  VARCHAR(64) NOT NULL DEFAULT '',
    owner_id     VARCHAR(64) NOT NULL DEFAULT '',
    status       VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user  VARCHAR(64) NOT NULL DEFAULT '',
    update_user  VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cost_center_code
    ON billing_cost_center(tenant_id, center_code);
CREATE INDEX IF NOT EXISTS idx_cost_center_parent ON billing_cost_center(tenant_id, parent_code);

-- 成本归集。
CREATE TABLE IF NOT EXISTS billing_cost_allocation (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    cost_center_id  INTEGER NOT NULL,
    reference_type  VARCHAR(64) NOT NULL,
    reference_id    VARCHAR(128) NOT NULL,
    amount          DECIMAL(18,6) NOT NULL,
    currency        VARCHAR(10) NOT NULL DEFAULT 'CNY',
    occurred_date   VARCHAR(10) NOT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user     VARCHAR(64) NOT NULL DEFAULT '',
    update_user     VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cost_allocation_reference
    ON billing_cost_allocation(cost_center_id, reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_cost_allocation_date
    ON billing_cost_allocation(occurred_date);

-- 企业审批状态机。
CREATE TABLE IF NOT EXISTS billing_approval (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    approval_no       VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    business_type     VARCHAR(32) NOT NULL,
    business_id       VARCHAR(128) NOT NULL,
    amount            DECIMAL(18,6) NOT NULL DEFAULT 0,
    status            VARCHAR(32) NOT NULL DEFAULT 'APPLIED',
    current_step      INTEGER NOT NULL DEFAULT 1,
    applicant         VARCHAR(64) NOT NULL,
    reviewer          VARCHAR(64) NOT NULL DEFAULT '',
    reason            VARCHAR(256) NOT NULL,
    decision_comment  VARCHAR(256) NOT NULL DEFAULT '',
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user       VARCHAR(64) NOT NULL DEFAULT '',
    update_user       VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_approval_no ON billing_approval(approval_no);
CREATE INDEX IF NOT EXISTS idx_approval_tenant_status
    ON billing_approval(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_approval_business
    ON billing_approval(business_type, business_id);

-- 分账明细。
CREATE TABLE IF NOT EXISTS billing_revenue_share (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    share_no          VARCHAR(64) NOT NULL,
    source_type       VARCHAR(32) NOT NULL,
    source_id         VARCHAR(128) NOT NULL,
    beneficiary_type  VARCHAR(32) NOT NULL,
    beneficiary_id    VARCHAR(64) NOT NULL,
    gross_amount      DECIMAL(18,6) NOT NULL,
    share_rate        DECIMAL(8,6) NOT NULL,
    share_amount      DECIMAL(18,6) NOT NULL,
    currency          VARCHAR(10) NOT NULL DEFAULT 'CNY',
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user       VARCHAR(64) NOT NULL DEFAULT '',
    update_user       VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_revenue_share_no ON billing_revenue_share(share_no);
CREATE INDEX IF NOT EXISTS idx_revenue_share_beneficiary
    ON billing_revenue_share(beneficiary_type, beneficiary_id, status);

-- 分账打款。
CREATE TABLE IF NOT EXISTS billing_payout (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    payout_no         VARCHAR(64) NOT NULL,
    beneficiary_type  VARCHAR(32) NOT NULL,
    beneficiary_id    VARCHAR(64) NOT NULL,
    amount            DECIMAL(18,6) NOT NULL,
    currency          VARCHAR(10) NOT NULL DEFAULT 'CNY',
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    paid_time         DATETIME,
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user       VARCHAR(64) NOT NULL DEFAULT '',
    update_user       VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payout_no ON billing_payout(payout_no);
CREATE INDEX IF NOT EXISTS idx_payout_beneficiary_status
    ON billing_payout(beneficiary_type, beneficiary_id, status);

-- 默认资源、计量器、税率和 MOCK 支付渠道。
INSERT OR IGNORE INTO billing_resource
    (resource_code, resource_name, unit, status, description, create_user, update_user)
VALUES
    ('AI_TOKEN', 'AI 模型 Token', 'TOKEN', 'ACTIVE', 'AI 输入输出 Token', 'system', 'system'),
    ('STORAGE_GB', '存储空间', 'GB', 'ACTIVE', '存储容量', 'system', 'system'),
    ('API_CALL', 'API 调用', 'COUNT', 'ACTIVE', '开放接口请求次数', 'system', 'system');

INSERT OR IGNORE INTO billing_meter_definition
    (resource_code, meter_name, unit, aggregation_type, status, create_user, update_user)
VALUES
    ('AI_TOKEN', 'AI Token 计量器', 'TOKEN', 'SUM', 'ACTIVE', 'system', 'system'),
    ('STORAGE_GB', '存储计量器', 'GB', 'AVERAGE', 'ACTIVE', 'system', 'system'),
    ('API_CALL', 'API 调用计量器', 'COUNT', 'COUNT', 'ACTIVE', 'system', 'system');

INSERT OR IGNORE INTO billing_quota_definition
    (resource_code, quota_name, unit, period, warning_threshold, status, create_user, update_user)
VALUES
    ('AI_TOKEN', 'AI Token 月额度', 'TOKEN', 'MONTH', 80, 'ACTIVE', 'system', 'system'),
    ('STORAGE_GB', '存储月额度', 'GB', 'MONTH', 80, 'ACTIVE', 'system', 'system'),
    ('API_CALL', 'API 调用月额度', 'COUNT', 'MONTH', 80, 'ACTIVE', 'system', 'system');

INSERT OR IGNORE INTO billing_payment_channel
    (channel_code, channel_name, driver_code, status, config_ref, create_user, update_user)
VALUES ('MOCK', '本地模拟支付', 'MOCK', 'ACTIVE', 'core.billing.payment.mock', 'system', 'system');

INSERT OR IGNORE INTO billing_tax_rule
    (country, tax_type, rate, status, create_user, update_user)
VALUES ('CN', 'VAT', 0.00, 'ACTIVE', 'system', 'system');

INSERT OR IGNORE INTO billing_price_rule
    (resource_code, rule_name, pricing_mode, unit_quantity, condition_json, tier_json,
     status, create_user, update_user)
VALUES
    ('AI_TOKEN', 'AI Token 标准价', 'UNIT', 1000, '{}', '[]', 'ACTIVE', 'system', 'system'),
    ('STORAGE_GB', '存储标准价', 'UNIT', 1, '{}', '[]', 'ACTIVE', 'system', 'system'),
    ('API_CALL', 'API 调用标准价', 'UNIT', 1, '{}', '[]', 'ACTIVE', 'system', 'system');

INSERT OR IGNORE INTO billing_price_version
    (rule_id, version_no, price, effective_time, status, create_user, update_user)
SELECT id, 1,
       CASE resource_code
           WHEN 'AI_TOKEN' THEN 0.01
           WHEN 'STORAGE_GB' THEN 0.10
           ELSE 0.001
       END,
       CURRENT_TIMESTAMP, 'ACTIVE', 'system', 'system'
FROM billing_price_rule
WHERE rule_name IN ('AI Token 标准价', '存储标准价', 'API 调用标准价');

INSERT OR IGNORE INTO billing_product
    (product_code, product_name, description, status, create_user, update_user)
VALUES ('CORE_PLATFORM', 'Core Platform', 'Core Platform 默认 SaaS 产品', 'ACTIVE', 'system', 'system');

INSERT OR IGNORE INTO billing_plan
    (product_id, plan_code, plan_name, billing_cycle, price, currency, status,
     current_version, trial_days, description, create_user, update_user)
SELECT id, 'FREE', 'Free', 'FREE', 0, 'CNY', 'ACTIVE', 1, 0,
       '适合体验与个人开发', 'system', 'system'
FROM billing_product WHERE product_code = 'CORE_PLATFORM';

INSERT OR IGNORE INTO billing_plan
    (product_id, plan_code, plan_name, billing_cycle, price, currency, status,
     current_version, trial_days, description, create_user, update_user)
SELECT id, 'PRO', 'Pro', 'MONTHLY', 99, 'CNY', 'ACTIVE', 1, 7,
       '适合专业开发团队', 'system', 'system'
FROM billing_product WHERE product_code = 'CORE_PLATFORM';

INSERT OR IGNORE INTO billing_plan
    (product_id, plan_code, plan_name, billing_cycle, price, currency, status,
     current_version, trial_days, description, create_user, update_user)
SELECT id, 'ENTERPRISE', 'Enterprise', 'YEARLY', 9999, 'CNY', 'ACTIVE', 1, 30,
       '企业合同、预算与成本中心', 'system', 'system'
FROM billing_product WHERE product_code = 'CORE_PLATFORM';

INSERT OR IGNORE INTO billing_plan_item
    (plan_id, item_type, resource_code, item_code, item_value, unit, create_user, update_user)
SELECT id, 'QUOTA', 'AI_TOKEN', '', 10000, 'TOKEN', 'system', 'system'
FROM billing_plan WHERE plan_code = 'FREE';
INSERT OR IGNORE INTO billing_plan_item
    (plan_id, item_type, resource_code, item_code, item_value, unit, create_user, update_user)
SELECT id, 'QUOTA', 'AI_TOKEN', '', 1000000, 'TOKEN', 'system', 'system'
FROM billing_plan WHERE plan_code = 'PRO';
INSERT OR IGNORE INTO billing_plan_item
    (plan_id, item_type, resource_code, item_code, item_value, unit, create_user, update_user)
SELECT id, 'QUOTA', 'AI_TOKEN', '', 100000000, 'TOKEN', 'system', 'system'
FROM billing_plan WHERE plan_code = 'ENTERPRISE';

INSERT OR IGNORE INTO billing_plan_version
    (plan_id, version_no, snapshot_json, effective_time, status, create_user, update_user)
SELECT id, 1, '{"seed":true}', CURRENT_TIMESTAMP, 'ACTIVE', 'system', 'system'
FROM billing_plan WHERE plan_code IN ('FREE', 'PRO', 'ENTERPRISE');
