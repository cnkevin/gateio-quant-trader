@echo off
REM ============================================================
REM  Gate.io 量化交易系统启动脚本 (Windows)
REM ============================================================
REM  功能：
REM    1. 设置环境变量（API Key 和 Secret）
REM    2. 解决 Java 9+ 的 Gson 反射访问警告
REM
REM  使用方法：
REM    1. 首次运行前，先设置环境变量：
REM       set GATE_API_KEY=你的API密钥
REM       set GATE_API_SECRET=你的API密钥
REM    2. 双击运行此脚本，或在命令行执行 start.bat
REM ============================================================

REM ──────────────────────────────────────────────────────────
REM  环境变量设置（请修改为你的实际密钥）
REM ──────────────────────────────────────────────────────────
REM 方式一：直接在脚本中设置（安全性较低，仅供测试）
REM set GATE_API_KEY=你的Gate.io_API_KEY
REM set GATE_API_SECRET=你的Gate.io_API_SECRET

REM 方式二：使用已设置的环境变量（推荐）
REM 在 Windows 系统环境变量中设置：
REM   GATE_API_KEY=你的Gate.io_API_KEY
REM   GATE_API_SECRET=你的Gate.io_API_SECRET
REM ──────────────────────────────────────────────────────────

REM 检查环境变量是否已设置
if "%GATE_API_KEY%"=="" (
    echo [错误] 环境变量 GATE_API_KEY 未设置！
    echo.
    echo 请先设置环境变量：
    echo   Windows: set GATE_API_KEY=你的API密钥
    echo          set GATE_API_SECRET=你的API密钥
    echo.
    echo 或在系统环境变量中永久设置：
    echo   控制面板 -^> 系统 -^> 高级系统设置 -^> 环境变量
    echo.
    pause
    exit /b 1
)

if "%GATE_API_SECRET%"=="" (
    echo [错误] 环境变量 GATE_API_SECRET 未设置！
    echo.
    echo 请先设置环境变量：
    echo   Windows: set GATE_API_KEY=你的API密钥
    echo          set GATE_API_SECRET=你的API密钥
    echo.
    pause
    exit /b 1
)

set "JAR_FILE=%~dp0target\gate-quant-trader-1.0.0-all.jar"

REM 检查 JAR 文件是否存在
if not exist "%JAR_FILE%" (
    echo [错误] JAR 文件不存在: %JAR_FILE%
    echo 请先运行 mvn clean package 构建项目
    pause
    exit /b 1
)

echo ============================================================
echo    Gate.io 量化交易系统启动中...
echo    API密钥: %GATE_API_KEY:~0,8%... (已隐藏)
echo ============================================================
echo.

REM 启动 Java 应用
java --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/java.util=ALL-UNNAMED ^
     -jar "%JAR_FILE%" %*

pause
