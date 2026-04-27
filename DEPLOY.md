# Gate.io 永续合约量化交易系统 - 部署指南

本文档提供生产环境下部署和运维的完整指导。

---

## 📋 目录

- [环境准备](#环境准备)
- [依赖安装](#依赖安装)
- [配置指南](#配置指南)
- [构建部署](#构建部署)
- [运行方式](#运行方式)
- [生产环境](#生产环境)
- [监控维护](#监控维护)
- [故障排查](#故障排查)

---

## 环境准备

### 硬件要求

| 项目 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 2 核 | 4 核+ |
| 内存 | 4 GB | 8 GB+ |
| 磁盘 | 10 GB 可用 | 50 GB+ SSD |
| 网络 | 10 Mbps 稳定连接 | 100 Mbps+ |

### 软件要求

| 软件 | 版本 | 说明 |
|------|------|------|
| Java (JDK) | 11 / 17 LTS | 必须安装 JDK，JRE 不足够 |
| Maven | 3.6.0+ | 构建工具 |
| MySQL | 8.0+ | 可选，仅数据持久化需要 |

### 检查 Java 环境

```bash
# 检查 Java 版本
java -version

# 检查 Maven 版本
mvn -version

# 应显示类似：
# openjdk version "17.x.x"
# Maven 3.x.x
```

---

## 依赖安装

### 方式一：Windows

#### 1. 安装 JDK

下载 [Adoptium Eclipse Temurin](https://adoptium.net/) 或 [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)

推荐使用 **JDK 17 LTS** 版本。

#### 2. 安装 Maven

1. 下载 [Maven 3.9+](https://maven.apache.org/download.cgi)
2. 解压到 `C:\apache-maven-3.9.x`
3. 添加环境变量：
   ```
   MAVEN_HOME = C:\apache-maven-3.9.x
   PATH = %MAVEN_HOME%\bin;...
   ```
4. 验证：`mvn -version`

### 方式二：Linux (Ubuntu/Debian)

```bash
# 安装 JDK 17
sudo apt update
sudo apt install openjdk-17-jdk -y

# 安装 Maven
sudo apt install maven -y

# 验证
java -version
mvn -version
```

### 方式三：macOS

```bash
# 使用 Homebrew 安装
brew install openjdk@17 maven

# 链接 Java（如果需要）
sudo ln -sfn $(brew --prefix)/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
```

---

## 配置指南

### 配置文件位置

```
项目根目录/
└── src/main/resources/
    ├── application.yml          # 主配置文件
    ├── schema.sql               # 数据库建表脚本
    └── logback.xml              # 日志配置
```

### 必需配置项

**⚠️ 重要：API 密钥通过环境变量设置，不存储在配置文件中**

```bash
# ========== 环境变量设置 ==========
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

编辑 `application.yml` 中的其他参数：

```yaml
# ========== 交易配置 ==========
trading:
  # 交易合约列表（格式：币种_USDT）
  contracts:
    - "BTC_USDT"
    # - "ETH_USDT"  # 可添加更多
  
  # ⚠️ 重要：首次运行务必设为 false
  live_trading: false              # false=模拟盘, true=实盘
  
  leverage: 5                      # 杠杆倍数 (1-100)
  
  # 每笔仓位占保证金的比例 (0.01-1.0)
  position_pct: 0.1
  
  # 止损止盈
  stop_loss_pct: 0.05              # 止损 5%
  take_profit_pct: 0.10            # 止盈 10%

# ========== 数据库配置（可选）==========
database:
  enabled: false                   # true=启用, false=禁用
  host: "localhost"
  port: 3306
  name: "quant_trader"
  username: "your_db_username"     # 数据库用户名
  password: "your_db_password"    # 数据库密码
```

### 获取 Gate.io API Key

1. 登录 [Gate.io](https://www.gate.io/)
2. 点击右上角 **账户** → **API 管理**
3. 点击 **创建 API Key**
4. 权限设置：
   - ✅ 合约读取
   - ✅ 合约交易
   - ❌ 提现（不要开启）
5. 妥善保存 API Key 和 Secret，**Secret 只显示一次**

### 永久配置环境变量

**Linux / macOS（永久）：**

```bash
# 添加到 ~/.bashrc 或 ~/.bash_profile
echo 'export GATE_API_KEY=你的API密钥' >> ~/.bashrc
echo 'export GATE_API_SECRET=你的API密钥' >> ~/.bashrc
source ~/.bashrc
```

**Windows（永久）：**
1. 右键 **此电脑** → **属性**
2. 点击 **高级系统设置**
3. 点击 **环境变量**
4. 在 **系统变量** 中新建：
   - `GATE_API_KEY` = 你的API密钥
   - `GATE_API_SECRET` = 你的API密钥

---

## 构建部署

### 构建 Fat JAR

```bash
# 进入项目目录
cd e:\work\gate1

# 清理并构建（跳过测试）
mvn clean package -DskipTests
```

构建成功后生成：
```
target/gate-quant-trader-1.0.0-all.jar
```

### 构建产物说明

| 文件 | 说明 |
|------|------|
| `gate-quant-trader-1.0.0-all.jar` | 可执行 JAR，包含所有依赖 |

---

## 运行方式

### 方式一：使用启动脚本（推荐）

```bash
# Linux / macOS
chmod +x start.sh
./start.sh

# Windows
start.bat
```

脚本会自动检测环境变量 `GATE_API_KEY` 和 `GATE_API_SECRET` 是否已设置。

### 方式二：直接运行（需要先设置环境变量）

```bash
# Linux / macOS
export GATE_API_KEY=你的API密钥
export GATE_API_SECRET=你的API密钥
java -jar target/gate-quant-trader-1.0.0-all.jar

# Windows
set GATE_API_KEY=你的API密钥
set GATE_API_SECRET=你的API密钥
java -jar target/gate-quant-trader-1.0.0-all.jar
```

**带参数运行示例：**

```bash
# 指定配置文件目录
java -Dconf.dir=/opt/quant/config -jar target/gate-quant-trader-1.0.0-all.jar

# 指定日志级别
java -Dlogging.level=DEBUG -jar target/gate-quant-trader-1.0.0-all.jar
```

### 方式二：后台运行（Linux/macOS）

```bash
# 使用 nohup 后台运行
nohup java -jar target/gate-quant-trader-1.0.0-all.jar > logs/console.log 2>&1 &

# 查看进程
ps aux | grep gate-quant-trader

# 查看日志
tail -f logs/quant-trader.log
```

### 方式三：Systemd 服务（Linux 生产推荐）

#### 1. 创建服务文件

```bash
sudo nano /etc/systemd/system/quant-trader.service
```

#### 2. 服务配置内容

```ini
[Unit]
Description=Gate.io Quant Trading System
After=network.target mysql.service

[Service]
Type=simple
User=your_username
WorkingDirectory=/path/to/gate1
ExecStart=/usr/bin/java -jar target/gate-quant-trader-1.0.0-all.jar
Restart=always
RestartSec=10

# 环境变量（可选）
Environment="JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64"

# 日志配置
StandardOutput=append:/var/log/quant-trader/stdout.log
StandardError=append:/var/log/quant-trader/stderr.log

[Install]
WantedBy=multi-user.target
```

#### 3. 启用服务

```bash
# 重载 systemd
sudo systemctl daemon-reload

# 启用开机自启
sudo systemctl enable quant-trader

# 启动服务
sudo systemctl start quant-trader

# 查看状态
sudo systemctl status quant-trader

# 查看日志
sudo journalctl -u quant-trader -f
```

### 方式四：Windows 服务

使用 [NSSM](https://nssm.cc/) 将 JAR 注册为 Windows 服务：

```powershell
# 下载 NSSM 并解压

# 注册服务
nssm install quant-trader "C:\path\to\java.exe" "-jar target\gate-quant-trader-1.0.0-all.jar"
nssm set quant-trader AppDirectory "C:\path\to\gate1"
nssm set quant-trader DisplayName "Gate.io Quant Trader"

# 启动服务
nssm start quant-trader

# 查看状态
nssm status quant-trader
```

### 方式五：Docker 部署（可选）

#### Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制构建产物
COPY target/gate-quant-trader-1.0.0-all.jar app.jar

# 复制配置文件（运行时覆盖）
COPY src/main/resources/application.yml config/

# 创建日志目录
RUN mkdir -p logs

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 构建与运行

```bash
# 构建镜像
docker build -t quant-trader:latest .

# 运行容器
docker run -d \
  --name quant-trader \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/config:/app/config \
  -e JAVA_OPTS="-Xmx512m" \
  quant-trader:latest
```

---

## 生产环境

### 安全建议

1. **API Key 权限最小化**
   - 仅开启合约读取和交易权限
   - 不要开启提现权限

2. **配置文件安全**
   ```bash
   # 设置配置文件权限（Linux）
   chmod 600 src/main/resources/application.yml
   ```

3. **使用环境变量存储敏感信息**
   ```yaml
   api:
     key: ${GATE_API_KEY}
     secret: ${GATE_API_SECRET}
   ```

4. **网络隔离**
   - 使用防火墙限制访问
   - 考虑使用 VPN 连接

### 风险控制

```yaml
trading:
  # 强烈建议设置最大持仓限制
  max_position_size: 0.3        # 单币种最大仓位（30%）
  max_total_exposure: 0.6        # 总仓位上限（60%）
  
  # 每日最大亏损限制
  daily_loss_limit: 0.05         # 亏损 5% 停止交易
```

### 高可用部署

```
                    ┌─────────────────┐
                    │   负载均衡器      │
                    │  (可选，健康检查)  │
                    └────────┬─────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
    ┌────▼────┐         ┌────▼────┐         ┌────▼────┐
    │ 实例 1  │         │ 实例 2  │         │ 实例 3  │
    │ BTC    │         │ ETH    │         │ ALT    │
    │ USDT   │         │ USDT   │         │ 币种    │
    └────────┘         └────────┘         └────────┘
         │                   │                   │
         └───────────────────┼───────────────────┘
                             │
                    ┌────────▼────────┐
                    │     MySQL       │
                    │   (共享数据库)   │
                    └─────────────────┘
```

> ⚠️ 注意：多实例部署时，每个实例应只交易部分合约，避免重复下单。

---

## 监控维护

### 日志位置

```
项目根目录/logs/
├── quant-trader.log      # 主日志
├── trade-signals.log    # 交易信号日志
└── archive/             # 历史日志归档
```

### 监控指标

1. **进程监控**
   ```bash
   # 检查进程是否存在
   ps aux | grep gate-quant-trader
   
   # 监控 CPU/内存
   top -p $(pgrep -f gate-quant-trader)
   ```

2. **日志关键词监控**
   
   重点关注以下关键词：
   - `ERROR` - 系统错误
   - `交易失败` - 订单执行问题
   - `连接断开` - 网络问题
   - `止损触发` / `止盈触发` - 风控事件

3. **磁盘空间监控**
   ```bash
   # 检查日志目录大小
   du -sh logs/
   
   # 日志轮转配置（logback.xml）
   <maxFileSize>10MB</maxFileSize>
   <maxHistory>30</maxHistory>
   ```

### 定期维护

| 任务 | 频率 | 说明 |
|------|------|------|
| 日志清理 | 每周 | 删除 30 天前日志 |
| 数据库备份 | 每日 | 备份 `quant_trader` 数据库 |
| 策略复盘 | 每周 | 分析交易信号和收益 |
| 配置检查 | 每月 | 检查 API Key、参数设置 |
| 系统更新 | 按需 | 更新 JDK、Maven 版本 |

---

## 故障排查

### 常见问题

#### 1. 构建失败

**症状：** `mvn package` 报错

**排查：**
```bash
# 查看详细错误
mvn clean package -X

# 常见原因：
# - JDK 版本不对（需要 11+）
# - 网络问题（Maven 依赖下载失败）
# - 编译错误（代码语法问题）
```

**解决方案：**
```bash
# 确认 JDK 版本
java -version

# 配置 Maven 镜像（如果网络慢）
# 编辑 ~/.m2/settings.xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

#### 2. 运行报错 "INVALID_KEY"

**症状：** API 认证失败

**排查：**
1. 检查 `application.yml` 中的 API Key 是否正确
2. 检查 API Key 是否开启了合约交易权限
3. 检查 Key 是否过期或被禁用

**解决方案：**
- 登录 Gate.io，重新获取 API Key
- 确认权限：合约读取 ✅ + 合约交易 ✅

#### 3. 数据库连接失败

**症状：** `数据库连接测试失败`

**排查：**
```bash
# 检查 MySQL 是否运行
mysql -u username -p -e "SELECT 1"

# 检查端口
telnet localhost 3306
```

**解决方案：**
1. 确认 MySQL 已启动
2. 检查 `application.yml` 数据库配置
3. 执行 `schema.sql` 初始化表结构

#### 4. 程序无响应

**症状：** 控制台无输出，程序假死

**排查：**
```bash
# 检查进程状态
ps aux | grep java

# 查看 Java 线程堆栈
jstack <pid>
```

**解决方案：**
- 重启程序
- 增加 JVM 内存：`java -Xmx1024m -jar ...`

#### 5. 交易信号不触发

**症状：** 策略长时间不交易

**排查：**
1. 检查 K 线数据是否正常获取
2. 查看日志中的指标值
3. 确认合约是否在交易列表中

**解决方案：**
- 运行回测功能验证策略有效性
- 调整指标参数（MACD/RSI/KD）
- 检查市场行情是否满足开仓条件

### 日志分析

```bash
# 查看最近 100 行错误日志
tail -100 logs/quant-trader.log | grep -i error

# 查看交易信号记录
tail -50 logs/trade-signals.log

# 实时监控日志
tail -f logs/quant-trader.log
```

### 联系支持

遇到无法解决的问题时，请提供：
1. 错误日志（`logs/quant-trader.log`）
2. 配置文件（脱敏处理 API Key）
3. 系统环境信息（Java 版本、操作系统）

---

## 快速检查清单

部署前确认以下项目：

- [ ] JDK 11+ 已安装并配置
- [ ] Maven 已安装
- [ ] Gate.io API Key 已创建并配置
- [ ] `application.yml` 关键配置已完成
- [ ] MySQL 已安装（启用数据持久化时）
- [ ] `schema.sql` 已执行（启用数据持久化时）
- [ ] 构建成功（`mvn clean package`）
- [ ] 首次运行使用 `live_trading: false` 模拟盘测试
- [ ] 日志目录已创建
- [ ] 防火墙已配置（如需要）

---

*最后更新：2026-04-27*
