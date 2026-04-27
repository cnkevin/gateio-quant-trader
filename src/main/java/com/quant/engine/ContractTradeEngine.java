package com.quant.engine;

import com.quant.config.AppConfig;
import com.quant.indicator.IndicatorEngine;
import com.quant.model.Candlestick;
import com.quant.model.IndicatorSnapshot;
import com.quant.model.TradeSignal.Signal;
import com.quant.service.MarketDataService;
import com.quant.service.TradeExecutionService;
import com.quant.strategy.MacdKdRsiStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单合约交易引擎（运行于独立线程）
 * <p>
 * 每个合约启动一个 ContractTradeEngine 实例，形成独立的策略执行线程。
 * 执行流程：
 * <pre>
 *   while (running) {
 *     1. 获取最新 K 线
 *     2. 计算技术指标
 *     3. 判断交易信号
 *     4. 执行开仓 / 平仓
 *     5. 等待下一个轮询周期
 *   }
 * </pre>
 * </p>
 */
public class ContractTradeEngine implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ContractTradeEngine.class);

    /** 合约名称 */
    private final String contract;

    /** 运行标志（volatile 保证多线程可见性） */
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 各服务组件（每个引擎实例独立）
    private final MarketDataService    marketDataService;
    private final IndicatorEngine      indicatorEngine;
    private final MacdKdRsiStrategy    strategy;
    private final TradeExecutionService tradeService;

    private final AppConfig.TradingConfig tradingCfg;

    public ContractTradeEngine(String contract) {
        this.contract           = contract;
        this.tradingCfg         = AppConfig.getInstance().getTrading();
        this.marketDataService  = new MarketDataService();
        this.indicatorEngine    = new IndicatorEngine();
        this.strategy           = new MacdKdRsiStrategy();
        this.tradeService       = new TradeExecutionService();
    }

    // ====================================================================
    //  生命周期
    // ====================================================================

    /**
     * 启动策略引擎（在新线程中调用 run()）
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread thread = new Thread(this, "engine-" + contract);
            thread.setDaemon(true);
            thread.start();
            log.info("策略引擎已启动 合约={}", contract);
        } else {
            log.warn("策略引擎已在运行中 合约={}", contract);
        }
    }

    /**
     * 停止策略引擎（优雅停止，等待当前轮次完成）
     */
    public void stop() {
        running.set(false);
        log.info("策略引擎停止信号已发送 合约={}", contract);
    }

    /** 判断引擎是否正在运行 */
    public boolean isRunning() {
        return running.get();
    }

    /** 获取合约名称 */
    public String getContract() {
        return contract;
    }

    // ====================================================================
    //  主循环
    // ====================================================================

    @Override
    public void run() {
        log.info("═══════════════════════════════════════════");
        log.info("  策略引擎启动 合约={} 周期={} 间隔={}s  ",
                contract, tradingCfg.getKlineInterval(), tradingCfg.getPollIntervalSec());
        log.info("  模式: {} | 杠杆: {}x | 仓位: {}  ",
                tradingCfg.isLiveTrading() ? "🔴 实盘" : "📌 模拟",
                tradingCfg.getLeverage(), String.format("%.0f%%", tradingCfg.getPositionPct() * 100));
        log.info("═══════════════════════════════════════════");

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                executeCycle();
            } catch (Exception e) {
                log.error("策略执行周期发生异常 合约={}, 将在{}s后重试", contract, tradingCfg.getPollIntervalSec(), e);
            }

            // 等待下一个轮询周期
            sleepSeconds(tradingCfg.getPollIntervalSec());
        }

        log.info("策略引擎已停止 合约={}", contract);
    }

    // ====================================================================
    //  单次执行周期
    // ====================================================================

    /**
     * 执行一个完整的策略判断周期
     */
    private void executeCycle() {
        log.debug("──── 开始策略周期 合约={} ────", contract);

        // ── 步骤1：获取 K 线 ──
        List<Candlestick> candles = marketDataService.getLatestKlines(
                contract,
                tradingCfg.getKlineInterval(),
                tradingCfg.getKlineLimit()
        );
        if (candles.isEmpty()) {
            log.warn("K线数据获取失败，跳过本次周期 合约={}", contract);
            return;
        }

        // ── 步骤2：计算技术指标 ──
        IndicatorSnapshot snapshot = indicatorEngine.calculate(contract, candles);
        if (snapshot == null) {
            log.warn("指标计算失败，跳过本次周期 合约={}", contract);
            return;
        }

        // 打印当前指标快照（INFO 级别，保证日志可读性）
        printIndicatorSummary(snapshot);

        // ── 步骤3：判断当前持仓状态 ──
        boolean hasPosition = tradeService.hasLongPosition(contract);

        // ── 步骤4：根据持仓状态生成信号并执行 ──
        if (hasPosition) {
            // 有持仓 → 检查平仓信号
            double entryPrice = tradeService.getEntryPrice(contract);
            Signal exitSignal = strategy.checkExitSignal(snapshot, entryPrice);
            if (exitSignal.isActionable()) {
                tradeService.executeSignal(exitSignal, contract);
            }
        } else {
            // 无持仓 → 检查开仓信号
            Signal entrySignal = strategy.checkEntrySignal(snapshot);
            if (entrySignal.isActionable()) {
                tradeService.executeSignal(entrySignal, contract);
            }
        }

        log.debug("──── 策略周期完成 合约={} ────", contract);
    }

    // ====================================================================
    //  日志输出辅助
    // ====================================================================

    /**
     * 打印指标摘要（格式化输出，方便人工阅读日志）
     */
    private void printIndicatorSummary(IndicatorSnapshot s) {
        log.info(
                "📊 [{}] 价格={} | MACD DIF={}({}) DEA={} BAR={} | "
                        + "KD K={} D={} | RSI={}{}",
                contract,
                String.format("%.4f", s.getClosePrice()),
                String.format("%.4f", s.getMacdDif()), s.getMacdDif() > 0 ? "↑0轴上" : "↓0轴下",
                String.format("%.4f", s.getMacdDea()),
                String.format("%.4f", s.getMacdBar()),
                String.format("%.1f", s.getKValue()),
                String.format("%.1f", s.getDValue()),
                String.format("%.1f", s.getRsiValue()),
                s.isRsiTopDivergence() ? " ⚠️顶背离" : ""
        );
    }

    // ====================================================================
    //  工具方法
    // ====================================================================

    private void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }
}
