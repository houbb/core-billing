-- ============================================
-- Phase 0: Billing Foundation Tables
-- ============================================

-- 1. billing_account: 商业账户
CREATE TABLE IF NOT EXISTS billing_account (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id    VARCHAR(64)  NOT NULL DEFAULT '',
    account_name VARCHAR(128) NOT NULL,
    account_type VARCHAR(32)  NOT NULL,           -- PERSONAL / ORGANIZATION
    status       VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / FROZEN / CLOSED
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user  VARCHAR(64) NOT NULL DEFAULT '',
    update_user  VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_ba_tenant ON billing_account(tenant_id);
CREATE INDEX IF NOT EXISTS idx_ba_type ON billing_account(account_type);

-- 2. billing_transaction: 交易流水（核心账本，只追加不修改不删除）
CREATE TABLE IF NOT EXISTS billing_transaction (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id       INTEGER NOT NULL,
    transaction_no   VARCHAR(64) NOT NULL UNIQUE,  -- TX202607160001
    transaction_type VARCHAR(32) NOT NULL,          -- TOP_UP / CONSUME / REFUND / ADJUST
    amount           DECIMAL(18,6) NOT NULL,
    direction        VARCHAR(10) NOT NULL,          -- IN / OUT
    reference_type   VARCHAR(64) NOT NULL DEFAULT '',
    reference_id     VARCHAR(64) NOT NULL DEFAULT '',
    description      VARCHAR(256) NOT NULL DEFAULT '',
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user      VARCHAR(64) NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_bt_account ON billing_transaction(account_id);
CREATE INDEX IF NOT EXISTS idx_bt_time ON billing_transaction(create_time);
-- 幂等约束：同一业务来源+业务ID 只能有一笔交易
CREATE UNIQUE INDEX IF NOT EXISTS idx_bt_idempotent ON billing_transaction(reference_type, reference_id);

-- 3. billing_operation_log: 操作审计日志
CREATE TABLE IF NOT EXISTS billing_operation_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id  INTEGER,
    operation   VARCHAR(64) NOT NULL,
    operator    VARCHAR(64) NOT NULL,
    reason      VARCHAR(256) NOT NULL DEFAULT '',
    detail      TEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_bol_account ON billing_operation_log(account_id);
CREATE INDEX IF NOT EXISTS idx_bol_time ON billing_operation_log(create_time);

-- 4. billing_balance_snapshot: 余额快照（Phase 1 启用，Phase 0 预留）
CREATE TABLE IF NOT EXISTS billing_balance_snapshot (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id    INTEGER NOT NULL,
    balance       DECIMAL(18,6) NOT NULL,
    snapshot_date VARCHAR(10) NOT NULL,
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_bbs_account_date ON billing_balance_snapshot(account_id, snapshot_date);