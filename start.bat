@echo off
REM ============================================================
REM  Gate.io 永续合约量化交易系统启动脚本 (Windows)
REM ============================================================
REM  功能：
REM    1. 检查环境变量配置
REM    2. 创建日志目录
REM    3. 启动 Java 应用程序
REM
REM  前置要求：
REM    1. 已构建项目：mvn clean package -DskipTests
REM    2. 已设置环境变量 GATE_API_KEY 和 GATE_API_SECRET
REM
REM  使用方法：
REM    双击运行此脚本，或在命令行执行 start.bat
REM ============================================================

setlocal enabledelayedexpansion

REM ──────────────────────────────────────────────────────────
REM  基础路径设置
REM ──────────────────────────────────────────────────────────
set "SCRIPT_DIR=%~dp0"
set "JAR_FILE=%SCRIPT_DIR%target\gate-quant-trader-1.0.0-all.jar"

REM ──────────────────────────────────────────────────────────
REM  环境变量检查
REM ──────────────────────────────────────────────────────────
if "%GATE_API_KEY%"=="" (
    echo.
    echo [错误] 环境变量 GATE_API_KEY 未设置！
    echo.
    echo 请先设置环境变量：
    echo   Windows CMD:  set GATE_API_KEY=你的API密钥
    echo                  set GATE_API_SECRET=你的API密钥
    echo.
    echo 或在系统环境变量中永久配置：
    echo   控制面板 -^> 系统 -^> 高级系统设置 -^> 环境变量
    echo.
    pause
    exit /b 1
)

if "%GATE_API_SECRET%"=="" (
    echo.
    echo [错误] 环境变量 GATE_API_SECRET 未设置！
    echo.
    echo 请先设置环境变量：
    echo   Windows CMD:  set GATE_API_KEY=你的API密钥
    echo                  set GATE_API_SECRET=你的API密钥
    echo.
    pause
    exit /b 1
)

REM ──────────────────────────────────────────────────────────
REM  JAR 文件检查
REM ──────────────────────────────────────────────────────────
if not exist "%JAR_FILE%" (
    echo.
    echo [错误] JAR 文件不存在: %JAR_FILE%
    echo.
    echo 请先构建项目：
    echo   mvn clean package -DskipTests
    echo.
    pause
    exit /b 1
)

REM ──────────────────────────────────────────────────────────
REM  创建日志目录
REM ──────────────────────────────────────────────────────────
if not exist "%SCRIPT_DIR%logs" (
    mkdir "%SCRIPT_DIR%logs"
    if !errorlevel! equ 0 (
        echo [信息] 日志目录已创建: logs\
    )
)

REM ──────────────────────────────────────────────────────────
REM  启动应用
REM ──────────────────────────────────────────────────────────
echo.
echo ============================================================
echo     Gate.io 永续合约量化交易系统
echo ============================================================
echo     JAR 文件: %JAR_FILE%
echo     API密钥:  %GATE_API_KEY:~0,8%...^(已隐藏^)
echo ============================================================
echo.

REM Java 9+ 反射访问配置（Gson/SDK 必需）
java --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED ^
     --add-opens java.base/java.util=ALL-UNNAMED ^
     --add-opens java.base/java.io=ALL-UNNAMED ^
     --add-opens java.base/java.net=ALL-UNNAMED ^
     --add-opens java.base/java.nio=ALL-UNNAMED ^
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED ^
     -jar "%JAR_FILE%" %*

echo.
echo 程序已退出，按任意键关闭...
pause >nul
