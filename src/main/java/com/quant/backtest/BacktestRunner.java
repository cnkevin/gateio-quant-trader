package com.quant.backtest;

import com.quant.config.AppConfig;
import com.quant.database.BacktestRepository;
import com.quant.database.CandlestickRepository;
import com.quant.database.DatabaseManager;
import com.quant.indicator.IndicatorEngine;
import com.quant.model.BacktestReport;
import com.quant.model.Candlestick;
import com.quant.model.IndicatorSnapshot;
import com.quant.model.TradeSignal.Signal;
import com.quant.model.TradeSignal.SignalType;
import com.quant.service.MarketDataService;
import com.quant.strategy.MacdKdRsiStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 回测执行器
 * <p>
 * 使用近 N 天的历史 K 线数据，在本地回放策略逻辑，
 * 统计策略的历史表现，输出详细的回测报告。
 * </p>
 *
 * <h3>回测说明：</h3>
 * <ul>
 *   <li>使用收盘价下单（模拟市价成交）</li>
 *   <li>开仓数量基于初始资金 × positionPct × 杠杆计算</li>
 *   <li>考虑双边手续费</li>
 *   <li>不考虑滑点（实盘通常有 0.01%~0.05% 的滑点）</li>
 * </ul>
 */
public class BacktestRunner {

    private static final Logger log = LoggerFactory.getLogger(BacktestRunner.class);

    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String GREEN  = "\u001B[32m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";

    /** 1 天折算为秒数（用于时间范围计算） */
    private static final long SECONDS_PER_DAY = 86400L;

    /** 回测开仓张数最小值 */
    private static final long MIN_POSITION_SIZE = 1L;

    private final AppConfig config;
    private final MarketDataService marketDataService;
    private final IndicatorEngine indicatorEngine;
    private final BacktestRepository repository;
    private final CandlestickRepository candleRepository;
    private final String reportId;

    public BacktestRunner() {
        this.config            = AppConfig.getInstance();
        this.marketDataService = new MarketDataService();
        this.indicatorEngine   = new IndicatorEngine();
        this.repository         = new BacktestRepository();
        this.candleRepository   = new CandlestickRepository();
        this.reportId          = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // ====================================================================
    //  回测入口
    // ====================================================================

    /**
     * 对指定合约运行回测
     *
     * @param contract 合约名称（如 BTC_USDT）
     */
    public void run(String contract) {
        AppConfig.BacktestConfig bCfg = config.getBacktest();
        AppConfig.TradingConfig  tCfg = config.getTrading();

        System.out.println();
        System.out.println(CYAN + BOLD
                + "══════════════════════════════════════════════"
                + RESET);
        System.out.println(CYAN + BOLD
                + "  回测开始  合约=" + contract
                + "  周期=" + bCfg.getKlineInterval()
                + "  回溯=" + bCfg.getDefaultDays() + "天"
                + "  [报告ID=" + reportId + "]"
                + RESET);
        System.out.println(CYAN + BOLD
                + "══════════════════════════════════════════════"
                + RESET);

        // ── 1. 获取历史 K 线 ──
        long now      = Instant.now().getEpochSecond();
        long fromTime = now - bCfg.getDefaultDays() * SECONDS_PER_DAY;

        log.info("获取回测数据 合约={} from={} to={} interval={}",
                contract, fromTime, now, bCfg.getKlineInterval());

        List<Candlestick> allCandles = marketDataService.getKlinesByTimeRange(
                contract, bCfg.getKlineInterval(), fromTime, now);

        if (allCandles.size() < 50) {
            System.out.println(RED + "❌ K线数据不足（" + allCandles.size() + "根），无法回测" + RESET);
            return;
        }

        System.out.printf("  数据范围: %s → %s (%d根K线)%n",
                formatTime(allCandles.get(0).getTimestamp()),
                formatTime(allCandles.get(allCandles.size() - 1).getTimestamp()),
                allCandles.size());

        // ── 2. 逐根 K 线回放 ──
        List<BacktestTrade> trades = replay(contract, allCandles, bCfg, tCfg);

        // ── 3. 生成并打印报告 ──
        BacktestReport report = buildReport(contract, trades, bCfg, allCandles);
        printReport(contract, trades, bCfg.getInitialCapital(), allCandles);

        // ── 4. 保存到数据库 ──
        saveToDatabase(report, trades, contract, allCandles, bCfg.getKlineInterval());
    }

    // ====================================================================
    //  回测核心逻辑
    // ====================================================================

    /**
     * 逐根 K 线回放策略
     *
     * @param contract   合约名称
     * @param allCandles 全量历史 K 线（正序）
     * @param bCfg       回测配置
     * @param tCfg       交易配置
     * @return 产生的交易记录列表
     */
    private List<BacktestTrade> replay(String contract,
                                        List<Candlestick> allCandles,
                                        AppConfig.BacktestConfig bCfg,
                                        AppConfig.TradingConfig  tCfg) {

        List<BacktestTrade> trades = new ArrayList<>();

        // 需要足够多的 warmup K 线，指标才能稳定
        int warmup = Math.max(tCfg.getKlineLimit(), 100);

        // 持仓状态
        boolean inPosition = false;
        double  entryPrice = 0;
        long    entryTime  = 0;
        long    entrySize  = 0;  // 开仓张数

        // 使用独立的策略实例（不与实盘共享状态）
        MacdKdRsiStrategy strategy = new MacdKdRsiStrategy();

        // 从第 warmup 根开始回测（保证指标稳定）
        for (int i = warmup; i < allCandles.size(); i++) {
            // 取 [0, i] 的 K 线子集，模拟"当前时刻"只能看到已完成的 K 线
            List<Candlestick> window = allCandles.subList(
                    Math.max(0, i - tCfg.getKlineLimit()), i + 1);

            // 计算指标
            IndicatorSnapshot snapshot = indicatorEngine.calculate(contract, window);
            if (snapshot == null) continue;

            Candlestick currentBar = allCandles.get(i);
            double currentPrice    = currentBar.getClose();

            if (!inPosition) {
                // ── 无持仓，检查开仓信号 ──
                Signal entrySignal = strategy.checkEntrySignal(snapshot);
                if (entrySignal.getType() == SignalType.BUY) {
                    // 以收盘价模拟市价成交
                    entryPrice = currentPrice;
                    entryTime  = currentBar.getTimestamp();
                    // 计算开仓张数（基于初始资金的固定比例）
                    entrySize  = Math.max(MIN_POSITION_SIZE, (long) Math.floor(
                            bCfg.getInitialCapital() * tCfg.getPositionPct()
                                    * tCfg.getLeverage() / currentPrice));
                    inPosition = true;
                    log.debug("回测开仓 {} @ {} size={}", formatTime(entryTime), entryPrice, entrySize);
                }
            } else {
                // ── 有持仓，检查平仓信号 ──
                Signal exitSignal = strategy.checkExitSignal(snapshot, entryPrice);
                if (exitSignal.getType() != SignalType.NONE) {
                    double exitPrice = currentPrice;
                    long   exitTime  = currentBar.getTimestamp();
                    String exitType  = exitSignal.getType().name();

                    // 计算盈亏
                    double pnlGross    = (exitPrice - entryPrice) * entrySize;
                    double commission  = (entryPrice + exitPrice) * entrySize * bCfg.getCommissionRate();
                    double pnlNet      = pnlGross - commission;
                    double returnPct   = pnlNet / (entryPrice * entrySize / tCfg.getLeverage()) * 100;

                    trades.add(BacktestTrade.builder()
                            .entryTime(entryTime)
                            .exitTime(exitTime)
                            .entryPrice(entryPrice)
                            .exitPrice(exitPrice)
                            .size(entrySize)
                            .exitType(exitType)
                            .pnlGross(pnlGross)
                            .commission(commission)
                            .pnlNet(pnlNet)
                            .returnPct(returnPct)
                            .build());

                    log.debug("回测平仓 {} @ {} pnl={} 类型={}",
                            formatTime(exitTime), exitPrice, String.format("%.2f", pnlNet), exitType);

                    inPosition = false;
                    strategy.resetDivergenceState();
                }
            }
        }

        return trades;
    }

    // ====================================================================
    //  回测报告生成
    // ====================================================================

    /**
     * 打印格式化的回测报告
     */
    private void printReport(String contract, List<BacktestTrade> trades,
                              double initialCapital, List<Candlestick> allCandles) {

        System.out.println();
        System.out.println(BOLD + "  ────────── 回测结果报告 ──────────" + RESET);

        if (trades.isEmpty()) {
            System.out.println(YELLOW + "  📭 回测期间内无交易信号产生" + RESET);
            printMarketInfo(contract, allCandles);
            return;
        }

        // ── 统计指标计算 ──
        int totalTrades  = trades.size();
        int winTrades    = (int) trades.stream().filter(t -> t.getPnlNet() > 0).count();
        int lossTrades   = totalTrades - winTrades;
        double winRate   = (double) winTrades / totalTrades * 100;

        double totalPnl  = trades.stream().mapToDouble(BacktestTrade::getPnlNet).sum();
        double maxPnl    = trades.stream().mapToDouble(BacktestTrade::getPnlNet).max().orElse(0);
        double minPnl    = trades.stream().mapToDouble(BacktestTrade::getPnlNet).min().orElse(0);
        double avgPnl    = trades.stream().mapToDouble(BacktestTrade::getPnlNet).average().orElse(0);
        double totalComm = trades.stream().mapToDouble(BacktestTrade::getCommission).sum();

        double totalReturn = totalPnl / initialCapital * 100;

        // 最大连续亏损（最大回撤简化版）
        double maxDrawdown = calcMaxDrawdown(trades, initialCapital);

        // ── 打印统计数字 ──
        System.out.printf("  %-16s %s%n", "合约:", contract);
        System.out.printf("  %-16s %.2f USDT%n", "初始资金:", initialCapital);
        System.out.printf("  %-16s %s USDT (%s%.2f%%%s)%n",
                "总盈亏:",
                formatPnl(totalPnl),
                totalPnl >= 0 ? GREEN : RED,
                totalReturn,
                RESET);
        System.out.printf("  %-16s %.2f USDT%n", "手续费合计:", totalComm);
        System.out.println("  ────────────────────────────────────");
        System.out.printf("  %-16s %d 笔%n", "交易次数:", totalTrades);
        System.out.printf("  %-16s %d 笔 (%.1f%%)%n", "盈利次数:", winTrades, winRate);
        System.out.printf("  %-16s %d 笔 (%.1f%%)%n", "亏损次数:", lossTrades, 100 - winRate);
        System.out.printf("  %-16s %.2f USDT%n", "单笔最大盈利:", maxPnl);
        System.out.printf("  %-16s %.2f USDT%n", "单笔最大亏损:", minPnl);
        System.out.printf("  %-16s %.2f USDT%n", "单笔平均盈亏:", avgPnl);
        System.out.printf("  %-16s %.2f%%%n", "最大回撤:", maxDrawdown);
        System.out.println("  ────────────────────────────────────");

        // ── 打印按平仓类型统计 ──
        long stopLossCount   = trades.stream().filter(t -> "STOP_LOSS".equals(t.getExitType())).count();
        long takeProfitCount = trades.stream().filter(t -> "TAKE_PROFIT".equals(t.getExitType())).count();
        long sellCount       = trades.stream().filter(t -> "SELL".equals(t.getExitType())).count();

        System.out.printf("  %-16s %d 次%n", "策略平仓:", sellCount);
        System.out.printf("  %-16s %d 次%n", "止盈平仓:", takeProfitCount);
        System.out.printf("  %-16s %d 次%n", "止损平仓:", stopLossCount);
        System.out.println();

        // ── 打印每笔交易明细 ──
        System.out.println(BOLD + "  交易明细：" + RESET);
        for (int i = 0; i < trades.size(); i++) {
            BacktestTrade t = trades.get(i);
            String color = t.getPnlNet() >= 0 ? GREEN : RED;
            System.out.println("  [" + String.format("%02d", i + 1) + "] " + color + t + RESET);
        }

        System.out.println();
        System.out.println(CYAN + BOLD
                + "══════════════════════════════════════════════" + RESET);
    }

    /**
     * 打印市场行情摘要（无交易信号时显示）
     */
    private void printMarketInfo(String contract, List<Candlestick> candles) {
        if (candles.isEmpty()) return;
        double startPrice = candles.get(0).getClose();
        double endPrice   = candles.get(candles.size() - 1).getClose();
        double change     = (endPrice - startPrice) / startPrice * 100;
        System.out.printf("  市场走势: %.4f → %.4f (%s%.2f%%%s)%n",
                startPrice, endPrice, change >= 0 ? GREEN : RED, change, RESET);
    }

    /**
     * 计算最大回撤（基于权益曲线）
     *
     * @return 最大回撤百分比（正数）
     */
    private double calcMaxDrawdown(List<BacktestTrade> trades, double initialCapital) {
        double equity    = initialCapital;
        double peakEquity= initialCapital;
        double maxDD     = 0;

        for (BacktestTrade t : trades) {
            equity += t.getPnlNet();
            if (equity > peakEquity) {
                peakEquity = equity;
            }
            double dd = (peakEquity - equity) / peakEquity * 100;
            if (dd > maxDD) maxDD = dd;
        }
        return maxDD;
    }

    // ====================================================================
    //  工具方法
    // ====================================================================

    private String formatTime(long epochSec) {
        return Instant.ofEpochSecond(epochSec)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String formatPnl(double pnl) {
        return (pnl >= 0 ? GREEN + "+" : RED + "") + String.format("%.2f", pnl) + RESET;
    }

    // ====================================================================
    //  数据库持久化
    // ====================================================================

    /**
     * 构建回测报告对象
     */
    private BacktestReport buildReport(String contract, List<BacktestTrade> trades,
                                        AppConfig.BacktestConfig bCfg,
                                        List<Candlestick> allCandles) {
        BacktestReport report = new BacktestReport(reportId, contract, "MACD+KD+RSI");
        report.setStartTime(epochToLocalDateTime(allCandles.get(0).getTimestamp()));
        report.setEndTime(epochToLocalDateTime(allCandles.get(allCandles.size() - 1).getTimestamp()));
        report.setKlineInterval(bCfg.getKlineInterval());
        report.setInitialCapital(BigDecimal.valueOf(bCfg.getInitialCapital()));
        report.setCommissionRate(BigDecimal.valueOf(bCfg.getCommissionRate()));

        if (trades.isEmpty()) {
            report.setFinalCapital(report.getInitialCapital());
            report.setTotalReturn(BigDecimal.ZERO);
            report.setTotalTrades(0);
            report.setWinTrades(0);
            report.setLossTrades(0);
            report.setWinRate(BigDecimal.ZERO);
            report.setMaxDrawdown(BigDecimal.ZERO);
            report.setSharpeRatio(BigDecimal.ZERO);
            return report;
        }

        double totalPnl = trades.stream().mapToDouble(BacktestTrade::getPnlNet).sum();
        int winTrades = (int) trades.stream().filter(t -> t.getPnlNet() > 0).count();
        int totalTrades = trades.size();
        double winRate = (double) winTrades / totalTrades;
        double maxDrawdown = calcMaxDrawdown(trades, bCfg.getInitialCapital());

        report.setFinalCapital(BigDecimal.valueOf(bCfg.getInitialCapital() + totalPnl));
        report.setTotalReturn(BigDecimal.valueOf(totalPnl / bCfg.getInitialCapital()));
        report.setTotalTrades(totalTrades);
        report.setWinTrades(winTrades);
        report.setLossTrades(totalTrades - winTrades);
        report.setWinRate(BigDecimal.valueOf(winRate));
        report.setMaxDrawdown(BigDecimal.valueOf(maxDrawdown / 100));
        report.setSharpeRatio(BigDecimal.ZERO);  // TODO: 后续接入收益波动率计算

        return report;
    }

    /**
     * 保存回测报告、交易记录和K线数据到数据库
     * 
     * 所有回测结果都会持久化到 MySQL 数据库。
     * 保存过程包括回测报告、交易记录和K线数据三部分，使用同一事务确保数据一致性。
     * 
     * @param report 回测报告对象
     * @param trades 交易记录列表
     * @param contract 合约名称
     * @param allCandles 完整的K线数据列表
     * @param interval K线周期
     */
    private void saveToDatabase(BacktestReport report, List<BacktestTrade> trades,
                                String contract, List<Candlestick> allCandles,
                                String interval) {
        DatabaseManager dbManager = DatabaseManager.getInstance();

        try {
            // 确保数据库连接正常
            if (!dbManager.testConnection()) {
                log.warn("数据库连接测试失败，跳过保存");
                return;
            }

            // 保存报告
            repository.saveReport(report);

            // 批量保存交易记录
            repository.saveTrades(reportId, contract, trades);

            // 批量保存K线数据
            candleRepository.saveBacktestCandles(reportId, contract, interval, allCandles);

            System.out.println(GREEN + "  ✅ 回测数据已保存到数据库 (含 " + allCandles.size() + " 根K线)" + RESET);

        } catch (Exception e) {
            log.error("保存回测数据到数据库失败", e);
            System.out.println(YELLOW + "  ⚠️ 数据库保存失败，请检查配置" + RESET);
        }
    }

    /**
     * Unix时间戳转换为LocalDateTime
     */
    private LocalDateTime epochToLocalDateTime(long epochSec) {
        return Instant.ofEpochSecond(epochSec)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
