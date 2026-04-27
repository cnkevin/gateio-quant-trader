#!/bin/bash
# ============================================================
#  Gate.io 量化交易系统启动脚本 (Linux/macOS)
# ============================================================
#  功能：
#    1. 设置环境变量（API Key 和 Secret）
#    2. 解决 Java 9+ 的 Gson 反射访问警告
#
#  使用方法：
#    1. 首次运行前，先设置环境变量：
#       export GATE_API_KEY=你的API密钥
#       export GATE_API_SECRET=你的API密钥
#    2. 或直接运行：
#       GATE_API_KEY=xxx GATE_API_SECRET=xxx ./start.sh
#    3. 首次需要: chmod +x start.sh
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/target/gate-quant-trader-1.0.0-all.jar"

# ──────────────────────────────────────────────────────────
#  环境变量检查
# ──────────────────────────────────────────────────────────
if [ -z "$GATE_API_KEY" ]; then
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
    echo "  方式三（永久配置），添加到 ~/.bashrc 或 ~/.zshrc）："
    echo "    echo 'export GATE_API_KEY=你的API密钥' >> ~/.bashrc"
    echo "    echo 'export GATE_API_SECRET=你的API密钥' >> ~/.bashrc"
    echo "    source ~/.bashrc"
    exit 1
fi

if [ -z "$GATE_API_SECRET" ]; then
    echo "[错误] 环境变量 GATE_API_SECRET 未设置！"
    exit 1
fi

# 检查 JAR 文件是否存在
if [ ! -f "$JAR_FILE" ]; then
    echo "[错误] JAR 文件不存在: $JAR_FILE"
    echo "请先运行 mvn clean package 构建项目"
    exit 1
fi

echo "============================================================"
echo "   Gate.io 量化交易系统启动中..."
echo "   API密钥: ${GATE_API_KEY:0:8}... (已隐藏)"
echo "============================================================"
echo ""

# 启动 Java 应用
exec java --add-opens java.base/java.lang=ALL-UNNAMED \
           --add-opens java.base/java.util=ALL-UNNAMED \
           -jar "$JAR_FILE" "$@"
