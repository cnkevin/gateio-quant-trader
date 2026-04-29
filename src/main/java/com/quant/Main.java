package com.quant;

import com.quant.config.AppConfig;
import com.quant.core.ConnectionChecker;
import com.quant.database.DatabaseManager;
import com.quant.engine.TradingEngineManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Scanner;

/**
 * 程序主入口 - 交互式控制台
 * <p>
 * 提供以下功能菜单：
 * <ol>
 *   <li>API 连接检测</li>
 *   <li>启动策略引擎（实盘/模拟）</li>
 *   <li>查看运行状态</li>
 *   <li>停止所有引擎</li>
 *   <li>退出程序</li>
 * </ol>
 * </p>
 *
 * <p>启动命令：{@code java -jar gate-quant-trader-1.0.0-all.jar}</p>
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /** ANSI 颜色代码（控制台彩色输出） */
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String CYAN   = "\u001B[36m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";

    private static TradingEngineManager engineManager;

    public static void main(String[] args) {
        // ── 1. 初始化配置 ──
        printBanner();
        log.info("正在加载配置文件...");

        try {
            AppConfig.load();
        } catch (Exception e) {
            System.out.println(RED + "❌ 配置文件加载失败：" + e.getMessage() + RESET);
            log.error("配置文件加载失败，程序退出", e);
            System.exit(1);
        }

        // ── 2. 数据库连接检测 ──
        DatabaseManager db = DatabaseManager.getInstance();
        System.out.println(CYAN + "⟹ 正在检测数据库连接..." + RESET);
        if (!db.testConnection()) {
            System.out.println(RED + "❌ 数据库连接失败！请检查 MySQL 配置（host/port/账号密码）后重试" + RESET);
            log.error("数据库连接检测失败，程序退出");
            System.exit(1);
        }
        System.out.println(GREEN + "✅ 数据库连接正常" + RESET);

        // ── 3. 注入日志系统属性（供 logback.xml 使用） ──
        AppConfig.LoggingConfig logCfg = AppConfig.getInstance().getLogging();
        System.setProperty("LOG_DIR", logCfg.getLogDir());
        System.setProperty("MAX_FILE_SIZE", logCfg.getMaxFileSizeMb() + "MB");
        System.setProperty("MAX_HISTORY", String.valueOf(logCfg.getMaxHistoryDays()));

        // ── 4. 初始化引擎管理器 ──
        engineManager = new TradingEngineManager();

        // ── 5. 注册 JVM 关闭钩子（优雅退出） ──
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM 关闭钩子触发，正在停止所有引擎...");
            engineManager.stopAll();
        }, "shutdown-hook"));

        // ── 6. 进入交互式菜单 ──
        runInteractiveMenu();
    }

    // ====================================================================
    //  交互式菜单
    // ====================================================================

    /**
     * 交互式主菜单循环
     */
    private static void runInteractiveMenu() {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            printMainMenu();
            System.out.print(BOLD + "请选择操作 [1-6]: " + RESET);

            String input = scanner.nextLine().trim();

            switch (input) {
                case "1":
                    doConnectionCheck();
                    break;
                case "2":
                    doStartEngines();
                    break;
                case "3":
                    doShowStatus();
                    break;
                case "4":
                    doStopEngines();
                    break;
                case "5":
                    doRunBacktest(scanner);
                    break;
                case "6":
                    doExit();
                    return;  // 正常退出，不会执行到这里（doExit 调用了 System.exit）
                default:
                    System.out.println(YELLOW + "⚠️  无效输入，请输入 1-6" + RESET);
            }

            // 操作完成后暂停，等待用户确认
            System.out.print("\n按 Enter 键继续...");
            scanner.nextLine();
        }
    }

    // ====================================================================
    //  菜单功能实现
    // ====================================================================

    /**
     * 功能1：API 连接检测（含数据库）
     * 
     * 本检测可帮助用户确认数据库和 API 的连接状态。
     */
    private static void doConnectionCheck() {
        System.out.println(CYAN + "\n⟹ 正在执行连接检测..." + RESET);

        // 数据库检测
        DatabaseManager db = DatabaseManager.getInstance();
        System.out.print("  [0] 数据库连接     ... ");
        if (db.testConnection()) {
            System.out.println(GREEN + "✅ 正常" + RESET);
        } else {
            System.out.println(RED + "❌ 失败（请检查 MySQL 配置）" + RESET);
        }

        // Gate.io API 检测
        ConnectionChecker checker = new ConnectionChecker();
        checker.runFullCheck();
    }

    /**
     * 功能2：启动交易引擎
     */
    private static void doStartEngines() {
        if (engineManager.isAnyRunning()) {
            System.out.println(YELLOW + "\n⚠️  已有引擎在运行，请先停止后再重新启动" + RESET);
            doShowStatus();
            return;
        }

        AppConfig.TradingConfig tCfg = AppConfig.getInstance().getTrading();
        System.out.println(CYAN + "\n⟹ 准备启动策略引擎" + RESET);
        System.out.println("   模式      : " + (tCfg.isLiveTrading() ? RED + "🔴 实盘（真实下单）" : GREEN + "📌 模拟（不下单）") + RESET);
        System.out.println("   合约列表  : " + tCfg.getContracts());
        System.out.println("   K线周期   : " + tCfg.getKlineInterval());
        System.out.println("   轮询间隔  : " + tCfg.getPollIntervalSec() + " 秒");
        System.out.println("   杠杆倍数  : " + tCfg.getLeverage() + "x");

        if (tCfg.isLiveTrading()) {
            System.out.print(RED + "\n⚠️  警告：实盘模式将使用真实资金交易！确认启动请输入 yes: " + RESET);
            Scanner scanner = new Scanner(System.in);
            String confirm = scanner.nextLine().trim();
            if (!confirm.equalsIgnoreCase("yes")) {
                System.out.println(YELLOW + "操作已取消" + RESET);
                return;
            }
        }

        engineManager.startAll();
        System.out.println(GREEN + "\n✅ 引擎启动成功！日志文件: logs/quant-trader.log" + RESET);
    }

    /**
     * 功能3：查看运行状态
     */
    private static void doShowStatus() {
        System.out.println(CYAN + "\n⟹ 引擎运行状态" + RESET);
        List<String> status = engineManager.getStatusReport();
        status.forEach(line -> System.out.println("  " + line));

        // 显示配置摘要
        AppConfig.TradingConfig tCfg = AppConfig.getInstance().getTrading();
        System.out.println();
        System.out.println("  当前配置:");
        System.out.printf("    %-12s %s%n", "交易模式:",   tCfg.isLiveTrading() ? "🔴 实盘" : "📌 模拟");
        System.out.printf("    %-12s %s%n", "结算币种:",   tCfg.getSettle().toUpperCase());
        System.out.printf("    %-12s %s%n", "K线周期:",    tCfg.getKlineInterval());
        System.out.printf("    %-12s %dx%n", "杠杆倍数:",  tCfg.getLeverage());
        System.out.printf("    %-12s %.0f%%%n", "仓位比例:", tCfg.getPositionPct() * 100);
        System.out.printf("    %-12s %.0f%%%n", "止损比例:", tCfg.getStopLossPct() * 100);
        System.out.printf("    %-12s %.0f%%%n", "止盈比例:", tCfg.getTakeProfitPct() * 100);
    }

    /**
     * 功能4：停止所有引擎
     */
    private static void doStopEngines() {
        if (!engineManager.isAnyRunning()) {
            System.out.println(YELLOW + "\n⚠️  当前没有运行中的引擎" + RESET);
            return;
        }
        System.out.println(CYAN + "\n⟹ 正在停止所有引擎..." + RESET);
        engineManager.stopAll();
        System.out.println(GREEN + "✅ 所有引擎已停止" + RESET);
    }

    /**
     * 功能5：运行回测
     */
    private static void doRunBacktest(Scanner scanner) {
        System.out.println(CYAN + "\n⟹ 启动回测程序" + RESET);
        AppConfig.BacktestConfig bCfg = AppConfig.getInstance().getBacktest();

        System.out.printf("  默认回测参数：%n");
        System.out.printf("    周期: 最近 %d 天%n", bCfg.getDefaultDays());
        System.out.printf("    K线: %s%n", bCfg.getKlineInterval());
        System.out.printf("    初始资金: %.0f USDT%n", bCfg.getInitialCapital());
        System.out.printf("    手续费: %.4f%%%n", bCfg.getCommissionRate() * 100);

        System.out.println("\n  请选择回测合约：");
        List<String> contracts = AppConfig.getInstance().getTrading().getContracts();
        for (int i = 0; i < contracts.size(); i++) {
            System.out.printf("    [%d] %s%n", i + 1, contracts.get(i));
        }

        System.out.print("  请输入序号（直接回车选择全部）: ");
        String sel = scanner.nextLine().trim();

        // 决定回测哪些合约
        List<String> targetContracts;
        if (sel.isEmpty()) {
            targetContracts = contracts;
        } else {
            try {
                int idx = Integer.parseInt(sel) - 1;
                if (idx < 0 || idx >= contracts.size()) {
                    System.out.println(YELLOW + "输入超出范围，将回测所有合约" + RESET);
                    targetContracts = contracts;
                } else {
                    targetContracts = List.of(contracts.get(idx));
                }
            } catch (NumberFormatException e) {
                System.out.println(YELLOW + "输入无效，将回测所有合约" + RESET);
                targetContracts = contracts;
            }
        }

        // 运行回测
        com.quant.backtest.BacktestRunner runner = new com.quant.backtest.BacktestRunner();
        for (String contract : targetContracts) {
            runner.run(contract);
        }
    }

    /**
     * 功能6：退出程序
     */
    private static void doExit() {
        System.out.println(CYAN + "\n⟹ 正在安全退出..." + RESET);
        engineManager.stopAll();
        DatabaseManager.getInstance().shutdown();
        System.out.println(GREEN + "再见！" + RESET);
        log.info("程序正常退出");
        System.exit(0);
    }

    // ====================================================================
    //  UI 辅助方法
    // ====================================================================

    /**
     * 打印程序启动横幅
     */
    private static void printBanner() {
        System.out.println();
        System.out.println(CYAN + BOLD);
        System.out.println("  ╔═══════════════════════════════════════════════════════╗");
        System.out.println("  ║        Gate.io 永续合约量化交易系统  v1.0.0           ║");
        System.out.println("  ║     MACD + KD + RSI 复合策略 / 多币种并发运行        ║");
        System.out.println("  ╚═══════════════════════════════════════════════════════╝");
        System.out.println(RESET);
    }

    /**
     * 打印主菜单
     */
    private static void printMainMenu() {
        System.out.println();
        System.out.println(BOLD + "  ──────────── 主菜单 ────────────" + RESET);
        System.out.println("  [1] 🔌 API 连接检测");
        System.out.println("  [2] 🚀 启动策略引擎");
        System.out.println("  [3] 📊 查看运行状态");
        System.out.println("  [4] ⏹  停止所有引擎");
        System.out.println("  [5] 📈 运行历史回测");
        System.out.println("  [6] 🚪 退出程序");
        System.out.println();
    }
}
