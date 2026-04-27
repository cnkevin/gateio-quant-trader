# Gate.io 永续合约量化交易系统

> 基于 Gate.io 官方 Java SDK（gateapi-java），实现 **MACD + KD + RSI** 复合策略的永续合约（U本位）量化交易程序。  
> 支持多币种并发运行、历史回测、交互式控制台操作。

---

## 📋 功能概览

| 功能 | 说明 |
|------|------|
| **多合约并发** | 每个合约独立线程，互不干扰 |
| **MACD + KD + RSI 策略** | 详见下方策略说明 |
| **模拟 / 实盘 双模式** | 配置一行参数切换，安全可控 |
| **API 连接检测** | 启动前全面检测网络、认证和合约可用性 |
| **历史回测** | 一键回测近 N 天数据，输出盈亏报告 |
| **数据库持久化** | 回测报告、交易记录、K线数据全量写入 MySQL（可选启用） |
| **彩色日志** | 控制台彩色输出 + 文件滚动存储 |
| **全参数可配置** | 所有指标参数、风控参数均可在 `application.yml` 中调整 |

---

## ⚙️ 系统要求

| 项目 | 版本要求 |
|------|---------|
| Java | **11** 或以上（推荐 OpenJDK 11 / 17） |
| Maven | 3.6.0 或以上 |
| 操作系统 | Windows / macOS / Linux（均支持） |
| 网络 | 能访问 `api.gateio.ws`（如需要请配置代理） |
| MySQL | **8.0+**（可选，启用数据持久化时需要） |

---

## 🚀 快速开始

### 第一步：获取源码

```bash
# 克隆仓库（或将源码解压到工作目录）
git clone <仓库地址>
cd gate-quant-trader
```

### 第二步：设置环境变量（API 密钥）

> **⚠️ 重要：** API 密钥通过环境变量设置，不存储在配置文件中

**设置环境变量：**

```bash
# Linux / macOS
export GATE_API_KEY=你的Gate.io_API_KEY
export GATE_API_SECRET=你的Gate.io_API_SECRET

# Windows (CMD)
set GATE_API_KEY=你的Gate.io_API_KEY
set GATE_API_SECRET=你的Gate.io_API_SECRET

# Windows (PowerShell)
$env:GATE_API_KEY="你的Gate.io_API_KEY"
$env:GATE_API_SECRET="你的Gate.io_API_SECRET"
```

> **如何创建 Gate.io API Key？**
> 登录 Gate.io → 顶部菜单 **账户** → **API 管理** → **创建 API Key**
> 权限需勾选：**合约读取**、**合约交易**（无需提现权限）

### 第三步：配置其他参数

编辑 `src/main/resources/application.yml`：

```yaml
trading:
  contracts:
    - "BTC_USDT"    # 交易比特币
    - "ETH_USDT"    # 同时交易以太坊（可选）
  live_trading: false  # 先设置 false 运行模拟模式
  leverage: 5          # 杠杆倍数
```

### 第四步：构建程序

```bash
mvn clean package -DskipTests
```

构建成功后，在 `target/` 目录下生成：
```
target/gate-quant-trader-1.0.0-all.jar   ← 可执行 Fat JAR（包含所有依赖）
```

### 第五步：运行程序

**使用启动脚本（推荐）：**

```bash
# Linux / macOS
chmod +x start.sh
./start.sh

# Windows
start.bat
```

**直接运行：**

```bash
# Linux / macOS
java -jar target/gate-quant-trader-1.0.0-all.jar

# Windows
java -jar target/gate-quant-trader-1.0.0-all.jar
```

你将看到交互式菜单：

```
  ╔═══════════════════════════════════════════════════════╗
  ║        Gate.io 永续合约量化交易系统  v1.0.0           ║
  ║     MACD + KD + RSI 复合策略 / 多币种并发运行        ║
  ╚═══════════════════════════════════════════════════════╝

  ──────────── 主菜单 ────────────
  [1] 🔌 API 连接检测
  [2] 🚀 启动策略引擎
  [3] 📊 查看运行状态
  [4] ⏹  停止所有引擎
  [5] 📈 运行历史回测
  [6] 🚪 退出程序
```

**建议操作顺序**：`[1] 连接检测` → `[5] 运行回测查看历史表现` → `[2] 启动引擎`

---

## 📈 交易策略说明

### 买入开仓条件（同时满足）

| 条件 | 说明 |
|------|------|
| MACD DIF > 0 | MACD 快线在 0 轴上方，代表多头格局 |
| KD 超卖区金叉 | K 和 D 值曾到达 20 轴以下，发生金叉并上穿 20 轴 |

### 卖出平仓条件（满足任一）

| 条件 | 优先级 | 说明 |
|------|--------|------|
| 止损 | 最高 | 价格跌破 `开仓价 × (1 - stop_loss_pct)` |
| 止盈 | 高 | 价格涨超 `开仓价 × (1 + take_profit_pct)` |
| RSI 顶背离平仓 | 普通 | RSI 顶背离出现后，RSI 跌破 70 线触发平仓 |

### 策略逻辑示意

```
K线序列:  ... [MACD DIF < 0] ... [MACD DIF > 0] ← 进入多头格局
                                      |
                              等待 KD 金叉...
                                      |
                              KD 在 20 轴附近金叉 ← 开仓 BUY
                                      |
                              持仓观察 RSI...
                                      |
                         价格创新高，但 RSI 没创新高 ← 顶背离出现
                                      |
                              等待 RSI 跌破 70...
                                      |
                              RSI 从 70+ 跌破 70 ← 平仓 SELL
```

---

## 🗂️ 项目结构

```
gate-quant-trader/
├── src/
│   ├── main/
│   │   ├── java/com/quant/
│   │   │   ├── Main.java                      # 程序入口（交互式菜单）
│   │   │   ├── config/
│   │   │   │   └── AppConfig.java             # 配置加载器（单例）
│   │   │   ├── core/
│   │   │   │   ├── GateApiClientFactory.java  # API Client 工厂
│   │   │   │   └── ConnectionChecker.java     # 连接检测工具
│   │   │   ├── model/
│   │   │   │   ├── Candlestick.java           # K线数据模型
│   │   │   │   ├── IndicatorSnapshot.java     # 指标快照模型
│   │   │   │   ├── TradeSignal.java           # 交易信号模型
│   │   │   │   ├── BacktestReport.java        # 回测报告实体
│   │   │   │   └── TradingRecord.java         # 实盘交易记录实体
│   │   │   ├── database/
│   │   │   │   ├── DatabaseManager.java       # 数据库连接管理（HikariCP）
│   │   │   │   ├── BacktestRepository.java    # 回测数据访问层
│   │   │   │   ├── CandlestickRepository.java # K线数据访问层
│   │   │   │   └── TradingRepository.java     # 实盘交易记录访问层
│   │   │   ├── service/
│   │   │   │   ├── MarketDataService.java     # 行情数据服务
│   │   │   │   └── TradeExecutionService.java # 交易执行服务
│   │   │   ├── indicator/
│   │   │   │   └── IndicatorEngine.java       # 技术指标计算（TA4J）
│   │   │   ├── strategy/
│   │   │   │   └── MacdKdRsiStrategy.java     # 策略信号生成器
│   │   │   ├── engine/
│   │   │   │   ├── ContractTradeEngine.java   # 单合约交易引擎
│   │   │   │   └── TradingEngineManager.java  # 多合约管理器
│   │   │   └── backtest/
│   │   │       ├── BacktestRunner.java        # 回测执行器
│   │   │       └── BacktestTrade.java         # 回测交易记录
│   │   └── resources/
│   │       ├── application.yml               # 主配置文件 ⭐
│   │       ├── schema.sql                    # 数据库建表脚本 ⭐
│   │       └── logback.xml                   # 日志配置
│   └── test/
│       └── java/                             # 单元测试（待扩展）
├── logs/                                     # 运行日志（自动生成）
│   ├── quant-trader.log                      # 主日志
│   ├── trade-signals.log                     # 交易信号专用日志
│   └── archive/                              # 历史日志归档
├── pom.xml                                   # Maven 构建文件
└── README.md                                 # 本文档
```

---

## ⚙️ 配置参数说明

完整配置文件位于 `src/main/resources/application.yml`，所有参数均有详细注释。

### 关键参数速查

| 参数路径 | 默认值 | 说明 |
|---------|--------|------|
| `api.key` | 无 | **必填** Gate.io API Key |
| `api.secret` | 无 | **必填** Gate.io API Secret |
| `trading.contracts` | `[BTC_USDT]` | 参与交易的合约列表 |
| `trading.live_trading` | `false` | `true`=实盘，`false`=模拟 |
| `trading.leverage` | `5` | 杠杆倍数（1-100） |
| `trading.position_pct` | `0.1` | 每笔仓位占账户余额的比例 |
| `trading.stop_loss_pct` | `0.05` | 止损比例（5%） |
| `trading.take_profit_pct` | `0.10` | 止盈比例（10%） |
| `trading.kline_interval` | `15m` | K线周期 |
| `trading.poll_interval_sec` | `60` | 策略轮询间隔（秒） |
| `indicator.macd.fast_period` | `12` | MACD 快线周期 |
| `indicator.macd.slow_period` | `26` | MACD 慢线周期 |
| `indicator.kd.k_period` | `9` | KD 的 K 值周期 |
| `indicator.kd.oversold_level` | `20` | KD 超卖线 |
| `indicator.rsi.period` | `14` | RSI 周期 |
| `indicator.rsi.overbought_level` | `70` | RSI 超买线 |
| `backtest.default_days` | `7` | 回测天数 |
| `database.enabled` | `false` | 是否启用 MySQL 数据持久化 |
| `database.host` | `localhost` | MySQL 主机地址 |
| `database.port` | `3306` | MySQL 端口 |
| `database.name` | `quant_trader` | 数据库名称 |
| `database.username` | 无 | 数据库用户名（**必填**） |
| `database.password` | 无 | 数据库密码 |

---

## 🗄️ 数据库持久化（可选）

程序内置 MySQL 持久化支持，**默认关闭**，无需数据库也可正常运行。启用后，每次回测会将以下数据完整写入数据库，方便后续分析和图表重建。

### 数据库表结构

| 表名 | 说明 |
|------|------|
| `backtest_reports` | 回测报告（胜率、收益率、最大回撤等汇总指标） |
| `backtest_trades` | 回测每笔交易明细（入场/出场价格、盈亏、持仓时长等） |
| `backtest_candles` | 回测使用的完整 K 线数据（OHLCV），支持事后图表重建 |
| `trading_records` | 实盘交易记录（订单ID、成交价格、状态） |
| `position_snapshots` | 实盘持仓快照 |
| `daily_stats` | 每日交易统计汇总 |
| `realtime_candles` | 实盘 K 线缓存（用于策略回补和状态恢复） |

### 启用步骤

**第一步：初始化数据库**

```sql
-- 在 MySQL 中执行 schema.sql（将 <用户名> 替换为实际数据库用户）
mysql -u <用户名> -p < src/main/resources/schema.sql
```

**第二步：修改配置**

编辑 `src/main/resources/application.yml`：

```yaml
database:
  enabled: true          # 改为 true 启用
  host: "localhost"
  port: 3306
  name: "quant_trader"
  username: "your_username"   # 填写实际数据库用户名
  password: "your_password"
```

**第三步：运行回测**

启用后运行回测，完成时将输出：
```
  ✅ 回测数据已保存到数据库 (含 N 根K线)
```

### 数据用途示例

```sql
-- 查询最近 10 次回测报告
SELECT report_id, contract, total_trades, win_rate, total_return, max_drawdown
FROM backtest_reports ORDER BY created_at DESC LIMIT 10;

-- 查询某次回测的完整 K 线（可用于重建行情图）
SELECT bar_time, open_price, high_price, low_price, close_price, volume
FROM backtest_candles WHERE report_id = 'ABCD1234' ORDER BY bar_time;

-- 查询某次回测的所有交易明细
SELECT entry_time, entry_price, exit_time, exit_price, pnl, return_pct
FROM backtest_trades WHERE report_id = 'ABCD1234' ORDER BY entry_time;
```

---

## 📝 日志说明

| 日志文件 | 内容 |
|---------|------|
| `logs/quant-trader.log` | 主运行日志（所有级别） |
| `logs/trade-signals.log` | 交易信号专用日志（开仓/平仓记录） |
| `logs/archive/` | 按天归档的历史日志 |

**日志级别调整**（`application.yml`）：
```yaml
logging:
  level: "DEBUG"   # DEBUG 可看到更详细的指标计算过程
```

---

## ⚠️ 风险提示

1. **本程序仅供学习研究使用**，不构成任何投资建议
2. 量化交易存在亏损风险，请务必先在**模拟模式**中充分测试
3. 开启实盘前，请确保已理解策略逻辑和风险参数
4. 建议设置合理的止损比例，控制单笔最大亏损
5. 高杠杆会放大盈利也会放大亏损，新手请从低杠杆开始（1x-3x）

---

## 🛠️ 常见问题

**Q：运行时报 `INVALID_KEY` 错误？**  
A：检查 `application.yml` 中的 `api.key` 和 `api.secret` 是否正确填写，以及 API Key 是否开启了合约交易权限。

**Q：K线数据获取失败？**  
A：确认网络能访问 `api.gateio.ws`，部分地区需要配置 HTTP 代理。

**Q：策略长时间没有信号？**  
A：属于正常现象。KD 超卖区金叉条件较严格，市场震荡时信号较少。可以通过回测功能验证策略历史表现。

**Q：如何添加新的交易合约？**  
A：编辑 `application.yml` 的 `trading.contracts` 列表，添加合约名称（格式如 `ETH_USDT`），重启程序即可。

**Q：如何修改为更短的 K 线周期？**  
A：修改 `trading.kline_interval`（如改为 `5m`），同时相应缩短 `trading.poll_interval_sec`（如改为 `300`）。

**Q：数据库启用后回测报错 "数据库连接测试失败"？**  
A：检查 MySQL 是否已启动，以及 `application.yml` 中的 `host`、`port`、`username`、`password` 是否正确。也可先执行 `schema.sql` 确认数据库和表已创建。

**Q：不想使用数据库，回测数据会丢失吗？**  
A：不会。数据库功能默认关闭（`database.enabled: false`），回测结果仍会完整打印到控制台，只是不会持久化存储。

**Q：回测 K 线数据量很大，保存会很慢吗？**  
A：`CandlestickRepository` 使用 JDBC 批量写入（每 1000 条提交一次），实际根数取决于回测天数和 K 线周期，通常可在数秒内完成。

---

## 📦 依赖说明

| 依赖 | 版本 | 用途 |
|------|------|------|
| `io.gate:gate-api` | 7.1.8 | Gate.io 官方 Java SDK |
| `org.ta4j:ta4j-core` | 0.16 | 金融技术分析库（MACD/RSI/KD） |
| `ch.qos.logback:logback-classic` | 1.5.18 | 日志实现 |
| `org.yaml:snakeyaml` | 2.3 | YAML 配置文件解析 |
| `org.projectlombok:lombok` | 1.18.34 | 代码简化（Getter/Builder 等） |
| `com.fasterxml.jackson.core:jackson-databind` | 2.17.2 | JSON 处理 |
| `mysql:mysql-connector-java` | 8.0.33 | MySQL JDBC 驱动（数据持久化） |
| `com.zaxxer:HikariCP` | 5.1.0 | 高性能数据库连接池（数据持久化） |

---

## 📄 开源协议

MIT License - 自由使用，风险自担。
