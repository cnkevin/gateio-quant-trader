# Gate.io 永续合约量化交易系统

> 基于 Gate.io 官方 Java SDK 实现 MACD + KD + RSI 复合策略的永续合约量化交易程序。
> 支持多币种并发运行、历史回测、交互式控制台操作。

---

## 📋 核心功能

| 功能 | 说明 |
|------|------|
| **多合约并发** | 每个合约独立线程，互不干扰 |
| **MACD + KD + RSI 复合策略** | 三重指标过滤，提升信号质量 |
| **模拟/实盘双模式** | 配置一行参数切换，安全可控 |
| **API 连接检测** | 启动前全面检测网络、认证和合约可用性 |
| **历史回测** | 一键回测近 N 天数据，输出盈亏报告 |
| **数据持久化** | 使用 MySQL 存储回测报告、交易记录、K线数据 |
| **彩色日志** | 控制台彩色输出 + 文件滚动存储 |
| **全参数可配置** | 所有指标参数、风控参数均可在配置文件中调整 |

---

## ⚙️ 系统要求

| 项目 | 版本要求 |
|------|---------|
| Java | **JDK 11** 或以上（推荐 OpenJDK 11/17） |
| Maven | 3.6.0 或以上 |
| 操作系统 | Windows / macOS / Linux（均支持） |
| 网络 | 能访问 `api.gateio.ws`（如需请配置代理） |
| MySQL | **8.0+**（必需，程序使用 MySQL 存储数据） |

---

## 🚀 快速开始

### 1. 获取源码
```bash
# 克隆仓库
git clone <仓库地址>
cd gateio-quant-trader
```

### 2. 设置 API 密钥（环境变量）
> **⚠️ 安全提示：API 密钥通过环境变量设置，不存储在配置文件中**

**Linux / macOS：**
```bash
export GATE_API_KEY=你的Gate.io_API_KEY
export GATE_API_SECRET=你的Gate.io_API_SECRET
```

**Windows (CMD)：**
```cmd
set GATE_API_KEY=你的Gate.io_API_KEY
set GATE_API_SECRET=你的Gate.io_API_SECRET
```

**Windows (PowerShell)：**
```powershell
$env:GATE_API_KEY="你的Gate.io_API_KEY"
$env:GATE_API_SECRET="你的Gate.io_API_SECRET"
```

**如何创建 Gate.io API Key？**
1. 登录 Gate.io → 顶部菜单 **账户** → **API 管理**
2. 点击 **创建 API Key**
3. 权限勾选：**合约读取**、**合约交易**（无需提现权限）

### 3. 配置交易参数
编辑 `src/main/resources/application.yml`：
```yaml
trading:
  contracts:
    - "BTC_USDT"        # 交易比特币（可添加多个）
    # - "ETH_USDT"
  live_trading: false   # 首次运行务必设为 false（模拟模式）
  leverage: 5           # 杠杆倍数
  position_pct: 0.1     # 每笔仓位占保证金比例
  stop_loss_pct: 0.05   # 止损比例 5%
  take_profit_pct: 0.10 # 止盈比例 10%
```

### 4. 构建程序
```bash
mvn clean package -DskipTests
```
构建成功后，在 `target/` 目录下生成可执行的 **Fat JAR**（包含所有依赖）。

### 5. 运行程序
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
java -jar target/gate-quant-trader-1.0.0-all.jar
```

运行后将看到交互式菜单，**建议操作顺序**：
`[1] API 连接检测` → `[5] 运行历史回测` → `[2] 启动策略引擎`

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

**策略逻辑示意：**
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

## 🗂️ 项目结构（关键目录）

```
gateio-quant-trader/
├── src/main/java/com/quant/
│   ├── Main.java                      # 程序入口（交互式菜单）
│   ├── config/AppConfig.java          # 配置加载器（单例）
│   ├── core/                          # 核心组件（API客户端、连接检测）
│   ├── model/                         # 数据模型（K线、指标、交易记录等）
│   ├── service/                       # 服务层（行情、交易执行）
│   ├── indicator/IndicatorEngine.java # 技术指标计算（TA4J）
│   ├── strategy/MacdKdRsiStrategy.java # 策略信号生成器
│   ├── engine/                        # 交易引擎（单合约、多合约管理）
│   ├── backtest/                      # 回测执行器
│   └── database/                      # 数据持久化（MySQL 操作）
├── src/main/resources/
│   ├── application.yml                # 主配置文件 ⭐
│   ├── schema.sql                     # 数据库建表脚本（必需）
│   └── logback.xml                    # 日志配置
├── logs/                              # 运行日志（自动生成）
├── pom.xml                            # Maven 构建文件
└── README.md                          # 本文档
```

---

## 🗄️ 数据持久化

程序使用 MySQL 数据库存储回测报告、交易记录和K线数据。启动程序前必须正确配置数据库连接，否则程序将无法启动。

### 配置步骤
1. **初始化数据库**：执行 `src/main/resources/schema.sql` 创建数据表
2. **修改配置**：在 `application.yml` 的 `database` 部分填写正确的连接参数
3. **启动程序**：程序会自动检测数据库连接，连接正常方可继续运行

**数据表说明：**
- `backtest_reports` - 回测报告（胜率、收益率、最大回撤等）
- `backtest_trades` - 回测每笔交易明细
- `backtest_candles` - 回测使用的完整 K 线数据（OHLCV）
- `trading_records` - 实盘交易记录
- `realtime_candles` - 实盘 K 线缓存

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
  level: "DEBUG"   # DEBUG 可查看详细指标计算过程
```

---

## ⚠️ 风险提示

1. **本程序仅供学习研究使用**，不构成任何投资建议
2. 量化交易存在亏损风险，请务必先在**模拟模式**中充分测试
3. 开启实盘前，请确保已理解策略逻辑和风险参数
4. 建议设置合理的止损比例，控制单笔最大亏损
5. 高杠杆会放大盈利也会放大亏损，新手请从低杠杆开始（1x-3x）

---

## ❓ 常见问题

**Q：运行时报 `INVALID_KEY` 错误？**  
A：检查 API Key 是否已开启合约交易权限，或重新创建 API Key。

**Q：K线数据获取失败？**  
A：确认网络能访问 `api.gateio.ws`，如需请配置 HTTP 代理。

**Q：策略长时间没有信号？**  
A：属于正常现象。KD 超卖区金叉条件较严格，市场震荡时信号较少。可通过回测验证策略历史表现。

**Q：如何添加新的交易合约？**  
A：编辑 `application.yml` 的 `trading.contracts` 列表，添加合约名称（格式如 `ETH_USDT`），重启程序即可。

**Q：数据库启用后回测报错 "数据库连接测试失败"？**  
A：检查 MySQL 是否已启动，以及数据库连接参数是否正确。也可先执行 `schema.sql` 确认数据库和表已创建。

---

## 📦 依赖说明

| 依赖 | 版本 | 用途 |
|------|------|------|
| `io.gate:gate-api` | 7.1.8 | Gate.io 官方 Java SDK |
| `org.ta4j:ta4j-core` | 0.16 | 金融技术分析库（MACD/RSI/KD） |
| `ch.qos.logback:logback-classic` | 1.5.18 | 日志实现 |
| `org.yaml:snakeyaml` | 2.3 | YAML 配置文件解析 |
| `org.projectlombok:lombok` | 1.18.34 | 代码简化（Getter/Builder 等） |
| `mysql:mysql-connector-java` | 8.0.33 | MySQL JDBC 驱动（数据持久化） |
| `com.zaxxer:HikariCP` | 5.1.0 | 高性能数据库连接池（数据持久化） |

---

## 📄 开源协议

MIT License - 自由使用，风险自担。

---

## 🔗 相关文档

- **[DEPLOY.md](./DEPLOY.md)** - 生产环境部署与运维详细指南
- **源码注释** - 关键类和方法均有详细中文注释，便于二次开发

---

## 👥 联系作者

如果你在学习和使用过程中遇到问题，或者有任何功能建议，欢迎扫码添加作者微信交流！

> ⏰ 添加时请备注 **「Gate量化」**，方便快速通过~

<img src="./images/wechat-qrcode.png" width="300" alt="微信二维码" />

> 💡 **提示**：Issues 和 Pull Request 也欢迎！