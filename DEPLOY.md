# Gate.io 永续合约量化交易系统 - 生产部署指南

本文档提供生产环境下部署、运维和监控的完整指导，适用于长期稳定运行的场景。

---

## 📋 目录

- [环境准备](#环境准备)
- [构建部署](#构建部署)
- [运行方式](#运行方式)
- [生产环境配置](#生产环境配置)
- [监控维护](#监控维护)
- [故障排查](#故障排查)
- [快速检查清单](#快速检查清单)

---

## 环境准备

### 系统要求
| 项目 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 2 核 | 4 核+ |
| 内存 | 4 GB | 8 GB+ |
| 磁盘 | 10 GB 可用 | 50 GB+ SSD |
| 网络 | 10 Mbps 稳定连接 | 100 Mbps+ |

### 软件要求
- **Java (JDK)**：11 或 17 LTS（必须安装 JDK，JRE 不足够）
- **Maven**：3.6.0+（仅构建时需要，运行时可不需要）
- **MySQL**：8.0+（必需，程序使用 MySQL 存储数据）

### 环境检查
```bash
# 检查 Java 版本
java -version

# 检查 Maven 版本（构建时）
mvn -version
```

> **提示**：如未安装 Java，请根据操作系统下载 [Adoptium Eclipse Temurin](https://adoptium.net/) 或 [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)，推荐 **JDK 17 LTS**。

---

## 构建部署

### 1. 获取源码
```bash
git clone <仓库地址>
cd gateio-quant-trader
```

### 2. 配置 API 密钥（环境变量）
**生产环境推荐使用环境变量存储敏感信息**  
具体设置方法请参考 [README.md](./README.md#2-设置-api-密钥环境变量)。

### 3. 调整配置文件
编辑 `src/main/resources/application.yml`，重点关注以下生产相关参数：
```yaml
trading:
  live_trading: true          # 实盘模式（确保已充分测试）
  max_position_size: 0.3      # 单币种最大仓位限制（建议）
  max_total_exposure: 0.6     # 总仓位上限（建议）

database:
  enabled: true               # 如需数据持久化则启用
  host: "数据库主机"
  port: 3306
  name: "quant_trader"
  username: "数据库用户"
  password: "数据库密码"      # 建议从环境变量读取
```

### 4. 构建可执行包
```bash
mvn clean package -DskipTests
```
构建成功后生成：`target/gate-quant-trader-1.0.0-all.jar`

---

## 运行方式

### 方式一：直接运行（适合测试）
```bash
# 设置环境变量后运行
java -jar target/gate-quant-trader-1.0.0-all.jar
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

#### 2. 服务配置
```ini
[Unit]
Description=Gate.io Quant Trading System
After=network.target mysql.service

[Service]
Type=simple
User=your_username
WorkingDirectory=/path/to/gateio-quant-trader
ExecStart=/usr/bin/java -jar target/gate-quant-trader-1.0.0-all.jar
Restart=always
RestartSec=10

# 环境变量（可在文件中直接设置）
Environment="GATE_API_KEY=你的API密钥"
Environment="GATE_API_SECRET=你的API密钥"

# 日志配置
StandardOutput=append:/var/log/quant-trader/stdout.log
StandardError=append:/var/log/quant-trader/stderr.log

[Install]
WantedBy=multi-user.target
```

#### 3. 启用服务
```bash
sudo systemctl daemon-reload
sudo systemctl enable quant-trader
sudo systemctl start quant-trader
sudo systemctl status quant-trader
```

### 方式四：Windows 服务（使用 NSSM）

1. 下载并安装 [NSSM](https://nssm.cc/)
2. 注册服务：
   ```powershell
   nssm install quant-trader "C:\path\to\java.exe" "-jar target\gate-quant-trader-1.0.0-all.jar"
   nssm set quant-trader AppDirectory "C:\path\to\gateio-quant-trader"
   nssm set quant-trader DisplayName "Gate.io Quant Trader"
   ```
3. 启动服务：`nssm start quant-trader`

### 方式五：Docker 部署（可选）

#### Dockerfile
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY target/gate-quant-trader-1.0.0-all.jar app.jar
COPY src/main/resources/application.yml config/

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
  -e GATE_API_KEY="你的API密钥" \
  -e GATE_API_SECRET="你的API密钥" \
  quant-trader:latest
```

---

## 生产环境配置

### 安全建议
1. **API Key 权限最小化**：仅开启合约读取和交易权限，**不要开启提现权限**
2. **配置分离**：将敏感参数（数据库密码）移至环境变量或专用配置文件
3. **网络隔离**：使用防火墙限制访问，考虑 VPN 连接
4. **定期更换密钥**：建议每 3-6 个月更换 API Key

### 风险控制参数
```yaml
trading:
  # 强烈建议设置以下风险控制参数
  max_position_size: 0.3        # 单币种最大仓位（30%）
  max_total_exposure: 0.6       # 总仓位上限（60%）
  daily_loss_limit: 0.05        # 亏损 5% 停止当日交易
```

### 高可用部署（可选）
如需多实例部署，建议每个实例负责不同合约，避免重复下单。共享 MySQL 数据库确保状态一致。

---

## 监控维护

### 日志监控
| 日志文件 | 监控重点 |
|---------|---------|
| `logs/quant-trader.log` | `ERROR` 级别的系统错误、网络连接问题 |
| `logs/trade-signals.log` | 开仓/平仓记录、交易执行状态 |
| 按日归档日志 | 定期检查历史异常 |

**关键日志关键词：**
- `ERROR` - 系统错误需立即处理
- `交易失败` - 订单执行问题
- `连接断开` - 网络问题
- `止损触发` / `止盈触发` - 风控事件

### 进程监控
```bash
# 检查进程状态
ps aux | grep gate-quant-trader

# 监控资源使用
top -p $(pgrep -f gate-quant-trader)

# 检查日志增长
du -sh logs/
```

### 数据库维护（如启用）
- **每日备份**：对 `quant_trader` 数据库进行完整备份
- **历史数据清理**：定期清理超过保留期限的回测数据
- **性能监控**：检查数据库连接数、慢查询

### 定期维护任务
| 任务 | 频率 | 说明 |
|------|------|------|
| 日志清理 | 每周 | 删除 30 天前日志（logback 自动归档） |
| 数据库备份 | 每日 | 备份 `quant_trader` 数据库 |
| 策略复盘 | 每周 | 分析交易信号和收益，调整参数 |
| 配置检查 | 每月 | 检查 API Key 状态、参数设置 |
| 系统更新 | 按需 | 更新 JDK 版本、安全补丁 |

---

## 故障排查

### 常见问题与解决

#### 1. 构建失败
**现象**：`mvn package` 报错  
**可能原因**：JDK 版本不匹配、网络问题、依赖下载失败  
**解决**：
```bash
# 确认 JDK 版本
java -version

# 尝试使用阿里云镜像
# 编辑 ~/.m2/settings.xml 添加镜像配置
```

#### 2. 运行时 "INVALID_KEY" 错误
**现象**：API 认证失败  
**解决**：
1. 检查 API Key 是否已开启合约交易权限
2. 确认环境变量是否正确设置
3. 登录 Gate.io 重新创建 API Key

#### 3. 数据库连接失败
**现象**：`数据库连接测试失败`  
**解决**：
1. 确认 MySQL 服务已启动
2. 检查 `application.yml` 中的数据库连接参数
3. 执行 `schema.sql` 初始化表结构

#### 4. 程序无响应
**现象**：控制台无输出，程序假死  
**解决**：
1. 检查进程状态：`ps aux | grep java`
2. 查看线程堆栈：`jstack <pid>`
3. 重启程序，可适当增加 JVM 内存：`java -Xmx1024m -jar ...`

#### 5. 交易信号不触发
**现象**：策略长时间不交易  
**解决**：
1. 检查 K 线数据是否正常获取
2. 查看日志中的指标值计算
3. 运行回测验证当前参数下的策略历史表现

### 日志分析命令
```bash
# 查看最近错误日志
tail -100 logs/quant-trader.log | grep -i error

# 查看交易信号
tail -50 logs/trade-signals.log

# 实时监控
tail -f logs/quant-trader.log
```

### 获取支持
遇到无法解决的问题时，请提供以下信息：
1. 错误日志（`logs/quant-trader.log` 相关部分）
2. 配置文件（脱敏处理 API Key 和数据库密码）
3. 系统环境（Java 版本、操作系统）

---

## 快速检查清单

部署前确认以下项目：

- [ ] JDK 11+ 已安装并配置
- [ ] Gate.io API Key 已创建并配置（合约读取+交易权限）
- [ ] `application.yml` 关键配置已完成（特别是 `live_trading` 设置）
- [ ] MySQL 已安装并运行（如需数据持久化）
- [ ] `schema.sql` 已执行（如需数据持久化）
- [ ] 构建成功（`mvn clean package`）
- [ ] 首次运行已通过模拟盘充分测试
- [ ] 防火墙/安全组已放行相关网络访问

---

**最后更新**：2026-04-29  
**相关文档**：[README.md](./README.md) - 基础使用与配置说明