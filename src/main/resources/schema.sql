-- ============================================================
--  Gate.io 量化交易系统 - MySQL 数据库表结构
-- ============================================================
-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS quant_trader CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE quant_trader;

-- ============================================================
--  1. 回测报告表 (backtest_reports)
-- ============================================================
CREATE TABLE IF NOT EXISTS backtest_reports (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    report_id       VARCHAR(64) NOT NULL UNIQUE COMMENT '回测报告唯一ID (UUID)',
    contract        VARCHAR(32) NOT NULL COMMENT '合约名称 (如 BTC_USDT)',
    strategy_name   VARCHAR(64) NOT NULL COMMENT '策略名称',

    -- 回测参数
    start_time      DATETIME NOT NULL COMMENT '回测开始时间',
    end_time        DATETIME NOT NULL COMMENT '回测结束时间',
    kline_interval  VARCHAR(16) NOT NULL COMMENT 'K线周期 (如 15m)',
    initial_capital DECIMAL(18, 4) NOT NULL COMMENT '初始资金',
    commission_rate DECIMAL(10, 6) NOT NULL COMMENT '手续费率',

    -- 回测结果
    final_capital   DECIMAL(18, 4) NOT NULL COMMENT '最终资金',
    total_return    DECIMAL(18, 8) NOT NULL COMMENT '总收益率',
    total_trades    INT NOT NULL COMMENT '总交易次数',
    win_trades      INT NOT NULL COMMENT '盈利交易次数',
    loss_trades     INT NOT NULL COMMENT '亏损交易次数',
    win_rate        DECIMAL(8, 4) NOT NULL COMMENT '胜率',
    max_drawdown    DECIMAL(8, 4) NOT NULL COMMENT '最大回撤',
    sharpe_ratio    DECIMAL(10, 4) DEFAULT 0 COMMENT '夏普比率',

    -- 元数据
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_contract_time (contract, created_at),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回测报告表';

-- ============================================================
--  2. 回测交易记录表 (backtest_trades)
-- ============================================================
CREATE TABLE IF NOT EXISTS backtest_trades (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    trade_id        VARCHAR(64) NOT NULL UNIQUE COMMENT '交易记录唯一ID',
    report_id       VARCHAR(64) NOT NULL COMMENT '关联的回测报告ID',
    contract        VARCHAR(32) NOT NULL COMMENT '合约名称',

    -- 交易信息
    signal_type     VARCHAR(32) NOT NULL COMMENT '信号类型 (BUY/SELL/STOP_LOSS/TAKE_PROFIT)',
    entry_time      DATETIME NOT NULL COMMENT '入场时间',
    entry_price     DECIMAL(18, 8) NOT NULL COMMENT '入场价格',
    exit_time       DATETIME COMMENT '出场时间',
    exit_price      DECIMAL(18, 8) COMMENT '出场价格',
    size            DECIMAL(18, 4) NOT NULL COMMENT '交易数量 (张)',
    direction        VARCHAR(32) NOT NULL COMMENT '交易方向 (LONG/SHORT)',

    -- 盈亏计算
    pnl             DECIMAL(18, 4) NOT NULL COMMENT '盈亏金额 (含手续费)',
    commission      DECIMAL(18, 4) NOT NULL COMMENT '手续费支出',
    return_pct      DECIMAL(12, 6) NOT NULL COMMENT '收益率百分比',

    -- 持仓信息
    holding_period  INT DEFAULT 0 COMMENT '持仓周期数 (K线根数)',
    max_profit      DECIMAL(18, 4) DEFAULT 0 COMMENT '最大浮盈',
    max_loss        DECIMAL(18, 4) DEFAULT 0 COMMENT '最大浮亏',

    -- 元数据
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_report_id (report_id),
    INDEX idx_contract (contract),
    INDEX idx_entry_time (entry_time),
    FOREIGN KEY (report_id) REFERENCES backtest_reports(report_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回测交易记录表';

-- ============================================================
--  3. 实盘交易记录表 (trading_records)
-- ============================================================
CREATE TABLE IF NOT EXISTS trading_records (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_id        VARCHAR(64) NOT NULL COMMENT '交易所订单ID',
    contract        VARCHAR(32) NOT NULL COMMENT '合约名称',

    -- 交易信息
    signal_type     VARCHAR(32) NOT NULL COMMENT '信号类型',
    signal_reason   VARCHAR(255) COMMENT '信号原因',
    direction       VARCHAR(32) NOT NULL COMMENT '交易方向 (OPEN_LONG/CLOSE_LONG/...)',
    size            DECIMAL(18, 4) NOT NULL COMMENT '交易数量 (张)',
    price           DECIMAL(18, 8) NOT NULL COMMENT '成交价格',
    order_type      VARCHAR(16) NOT NULL COMMENT '订单类型 (MARKET/LIMIT)',

    -- 状态
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '订单状态 (PENDING/SUCCESS/FAILED)',
    error_message   TEXT COMMENT '错误信息',

    -- 盈亏 (平仓时填写)
    pnl             DECIMAL(18, 4) COMMENT '盈亏金额',
    commission      DECIMAL(18, 4) COMMENT '手续费',

    -- 时间戳
    signal_time     DATETIME NOT NULL COMMENT '信号产生时间',
    order_time      DATETIME COMMENT '下单时间',
    fill_time       DATETIME COMMENT '成交时间',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_contract_time (contract, created_at),
    INDEX idx_order_id (order_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实盘交易记录表';

-- ============================================================
--  4. 持仓快照表 (position_snapshots)
-- ============================================================
CREATE TABLE IF NOT EXISTS position_snapshots (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    contract        VARCHAR(32) NOT NULL COMMENT '合约名称',
    trade_record_id VARCHAR(64) COMMENT '关联的交易记录ID',

    -- 持仓信息
    direction       VARCHAR(32) NOT NULL COMMENT '持仓方向 (LONG/SHORT)',
    size            DECIMAL(18, 4) NOT NULL COMMENT '持仓数量',
    entry_price     DECIMAL(18, 8) NOT NULL COMMENT '开仓价格',
    current_price   DECIMAL(18, 8) COMMENT '当前价格',
    unrealized_pnl  DECIMAL(18, 4) DEFAULT 0 COMMENT '未实现盈亏',
    leverage        INT NOT NULL COMMENT '杠杆倍数',

    -- 时间戳
    snapshot_time   DATETIME NOT NULL COMMENT '快照时间',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_contract_time (contract, snapshot_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='持仓快照表';

-- ============================================================
--  5. 每日统计表 (daily_stats)
-- ============================================================
CREATE TABLE IF NOT EXISTS daily_stats (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    stat_date       DATE NOT NULL COMMENT '统计日期',
    contract        VARCHAR(32) NOT NULL COMMENT '合约名称',

    -- 交易统计
    total_trades    INT DEFAULT 0 COMMENT '当日交易次数',
    winning_trades   INT DEFAULT 0 COMMENT '盈利次数',
    losing_trades    INT DEFAULT 0 COMMENT '亏损次数',
    total_pnl       DECIMAL(18, 4) DEFAULT 0 COMMENT '当日总盈亏',
    total_commission DECIMAL(18, 4) DEFAULT 0 COMMENT '当日总手续费',

    -- 资金统计
    opening_balance DECIMAL(18, 4) COMMENT '开盘余额',
    closing_balance DECIMAL(18, 4) COMMENT '收盘余额',

    -- 回撤
    max_drawdown    DECIMAL(18, 4) DEFAULT 0 COMMENT '当日最大回撤',

    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_date_contract (stat_date, contract),
    INDEX idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日统计表';

-- ============================================================
--  6. 回测K线数据表 (backtest_candles)
-- ============================================================
-- 存储回测过程中使用的完整K线数据，便于回溯分析和图表重建
CREATE TABLE IF NOT EXISTS backtest_candles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    report_id       VARCHAR(64) NOT NULL COMMENT '关联的回测报告ID',

    -- K线基本信息
    contract        VARCHAR(32) NOT NULL COMMENT '合约名称 (如 BTC_USDT)',
    kline_period    VARCHAR(16) NOT NULL COMMENT 'K线周期 (如 15m)',

    -- 时间序列
    bar_time        DATETIME NOT NULL COMMENT 'K线时间 (UTC)',

    -- OHLCV 数据
    open_price      DECIMAL(18, 8) NOT NULL COMMENT '开盘价',
    high_price      DECIMAL(18, 8) NOT NULL COMMENT '最高价',
    low_price       DECIMAL(18, 8) NOT NULL COMMENT '最低价',
    close_price     DECIMAL(18, 8) NOT NULL COMMENT '收盘价',
    volume          DECIMAL(18, 4) NOT NULL COMMENT '成交量 (张数)',
    quote_volume    DECIMAL(18, 8) COMMENT '成交额 (USDT)',

    -- 附加指标 (可选存储)
    macd_value      DECIMAL(12, 4) COMMENT 'MACD 值',
    macd_signal     DECIMAL(12, 4) COMMENT 'MACD Signal',
    macd_histogram  DECIMAL(12, 4) COMMENT 'MACD 柱 (DIF-DEA)',
    k_value         DECIMAL(8, 4) COMMENT 'KDJ K值',
    d_value         DECIMAL(8, 4) COMMENT 'KDJ D值',
    rsi_value       DECIMAL(8, 4) COMMENT 'RSI 值',

    -- 元数据
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_report_time (report_id, bar_time),
    INDEX idx_report_contract (report_id, contract),
    INDEX idx_bar_time (bar_time),
    FOREIGN KEY (report_id) REFERENCES backtest_reports(report_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回测K线数据表';

-- ============================================================
--  7. 实盘K线缓存表 (realtime_candles)
-- ============================================================
-- 存储实盘运行中获取的最新K线数据，支持策略回补和状态恢复
CREATE TABLE IF NOT EXISTS realtime_candles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',

    -- K线基本信息
    contract        VARCHAR(32) NOT NULL COMMENT '合约名称 (如 BTC_USDT)',
    kline_period    VARCHAR(16) NOT NULL COMMENT 'K线周期 (如 15m)',
    symbol_name      VARCHAR(32) NOT NULL COMMENT '交易对标识',

    -- 时间序列
    bar_time        DATETIME NOT NULL COMMENT 'K线时间 (UTC)',

    -- OHLCV 数据
    open_price      DECIMAL(18, 8) NOT NULL COMMENT '开盘价',
    high_price      DECIMAL(18, 8) NOT NULL COMMENT '最高价',
    low_price       DECIMAL(18, 8) NOT NULL COMMENT '最低价',
    close_price     DECIMAL(18, 8) NOT NULL COMMENT '收盘价',
    volume          DECIMAL(18, 4) NOT NULL COMMENT '成交量 (张数)',
    quote_volume    DECIMAL(18, 8) COMMENT '成交额 (USDT)',

    -- 元数据
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 唯一约束：同一合约+周期+时间只能有一条记录
    UNIQUE KEY uk_realtime_candle (contract, kline_period, bar_time),
    INDEX idx_contract_interval (contract, kline_period),
    INDEX idx_bar_time (bar_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实盘K线缓存表';
