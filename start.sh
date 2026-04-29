#!/bin/bash
# ============================================================
#  Gate.io 永续合约量化交易系统启动脚本 (Linux/macOS)
# ============================================================
#  功能：
#    1. 检查环境变量配置
#    2. 创建日志目录
#    3. 启动 Java 应用程序
#
#  前置要求：
#    1. 已构建项目：mvn clean package -DskipTests
#    2. 已设置环境变量 GATE_API_KEY 和 GATE_API_SECRET
#
#  使用方法：
#    首次运行：chmod +x start.sh
#    运行脚本：./start.sh
#
#  环境变量配置方式：
#    方式一（当前会话）：
#      export GATE_API_KEY=你的API密钥
#      export GATE_API_SECRET=你的API密钥
#      ./start.sh
#
#    方式二（一次性）：
#      GATE_API_KEY=xxx GATE_API_SECRET=xxx ./start.sh
#
#    方式三（永久配置，添加到 ~/.bashrc 或 ~/.zshrc）：
#      echo 'export GATE_API_KEY=你的API密钥' >> ~/.bashrc
#      echo 'export GATE_API_SECRET=你的API密钥' >> ~/.bashrc
#      source ~/.bashrc
# ============================================================

set -e

# ──────────────────────────────────────────────────────────
#  路径设置
# ──────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/target/gate-quant-trader-1.0.0-all.jar"

# ──────────────────────────────────────────────────────────
#  环境变量检查
# ──────────────────────────────────────────────────────────
if [ -z "$GATE_API_KEY" ]; then
    echo ""
    echo "[错误] 环境变量 GATE_API_KEY 未设置！"
    echo ""
    echo "请先设置环境变量："
    echo "  方式一（当前会话）："
    echo "    export GATE_API_KEY=你的API密钥"
    echo "    export GATE_API_SECRET=你的API密钥"
    echo ""
    echo "  方式二（一次性）："
    echo "    GATE_API_KEY=xxx GATE_API_SECRET=xxx ./start.sh"
    echo ""
    echo "  方式三（永久配置，添加到 ~/.bashrc 或 ~/.zshrc）："
    echo "    echo 'export GATE_API_KEY=你的API密钥' >> ~/.bashrc"
    echo "    echo 'export GATE_API_SECRET=你的API密钥' >> ~/.bashrc"
    echo "    source ~/.bashrc"
    exit 1
fi

if [ -z "$GATE_API_SECRET" ]; then
    echo ""
    echo "[错误] 环境变量 GATE_API_SECRET 未设置！"
    echo ""
    echo "请先设置环境变量："
    echo "  export GATE_API_SECRET=你的API密钥"
    exit 1
fi

# ──────────────────────────────────────────────────────────
#  JAR 文件检查
# ──────────────────────────────────────────────────────────
if [ ! -f "$JAR_FILE" ]; then
    echo ""
    echo "[错误] JAR 文件不存在: $JAR_FILE"
    echo ""
    echo "请先构建项目："
    echo "  mvn clean package -DskipTests"
    exit 1
fi

# ──────────────────────────────────────────────────────────
#  创建日志目录
# ──────────────────────────────────────────────────────────
if [ ! -d "$SCRIPT_DIR/logs" ]; then
    mkdir -p "$SCRIPT_DIR/logs"
    echo "[信息] 日志目录已创建: logs/"
fi

# ──────────────────────────────────────────────────────────
#  启动应用
# ──────────────────────────────────────────────────────────
echo ""
echo "============================================================"
echo "     Gate.io 永续合约量化交易系统"
echo "============================================================"
echo "     JAR 文件: $JAR_FILE"
echo "     API密钥:  ${GATE_API_KEY:0:8}...(已隐藏)"
echo "============================================================"
echo ""

# Java 9+ 反射访问配置（Gson/SDK 必需）
exec java \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
    --add-opens java.base/java.util=ALL-UNNAMED \
    --add-opens java.base/java.io=ALL-UNNAMED \
    --add-opens java.base/java.net=ALL-UNNAMED \
    --add-opens java.base/java.nio=ALL-UNNAMED \
    --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
    -jar "$JAR_FILE" "$@"
