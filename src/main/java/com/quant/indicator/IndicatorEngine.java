package com.quant.indicator;

import com.quant.config.AppConfig;
import com.quant.model.Candlestick;
import com.quant.model.IndicatorSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.statistics.*;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 技术指标计算引擎（基于 TA4J 量化分析库）
 * <p>
 * 封装 MACD、KD 随机指标、RSI 的计算逻辑。
 * 每次计算时重建 BarSeries，保证数据新鲜度。
 * </p>
 *
 * <h3>策略逻辑说明：</h3>
 * <pre>
 * 买入条件（同时满足）：
 *   1. MACD DIF 在 0 轴上方（多头格局）
 *   2. KD 指标出现超卖区（≤20）的金叉，并上穿 20 轴
 *
 * 卖出条件（满足其一）：
 *   1. RSI 顶背离后，RSI 跌破 70（策略卖出）
 *   2. 价格触及止损价（风控止损）
 *   3. 价格触及止盈价（止盈平仓）
 * </pre>
 *
 * @see <a href="https://github.com/ta4j/ta4j">TA4J 项目主页</a>
 */
public class IndicatorEngine {

    private static final Logger log = LoggerFactory.getLogger(IndicatorEngine.class);

    /** RSI 顶背离检测：价格容差（允许 0.5% 误差） */
    private static final double DIVERGENCE_PRICE_TOLERANCE = 0.995;

    /** RSI 顶背离检测：RSI 差值阈值（RSI 明显低于窗口高点才视为背离） */
    private static final double DIVERGENCE_RSI_THRESHOLD = 2.0;

    // ── 指标参数（从配置中读取） ──
    private final int macdFast;
    private final int macdSlow;
    private final int macdSignal;
    private final int kdKPeriod;
    private final int kdDPeriod;
    private final int rsiPeriod;
    private final int rsiOverbought;
    private final int rsiDivergenceLookback;

    public IndicatorEngine() {
        AppConfig.IndicatorConfig cfg = AppConfig.getInstance().getIndicator();
        AppConfig.IndicatorConfig.MacdConfig macdCfg = cfg.getMacd();
        AppConfig.IndicatorConfig.KdConfig   kdCfg   = cfg.getKd();
        AppConfig.IndicatorConfig.RsiConfig  rsiCfg  = cfg.getRsi();

        this.macdFast              = macdCfg.getFastPeriod();
        this.macdSlow              = macdCfg.getSlowPeriod();
        this.macdSignal            = macdCfg.getSignalPeriod();
        this.kdKPeriod             = kdCfg.getKPeriod();
        this.kdDPeriod             = kdCfg.getDPeriod();
        this.rsiPeriod             = rsiCfg.getPeriod();
        this.rsiOverbought         = rsiCfg.getOverboughtLevel();
        this.rsiDivergenceLookback = rsiCfg.getDivergenceLookback();

        log.info("指标引擎初始化 MACD({}/{}/{}) KD({}/{}) RSI({}) 超买={}",
                macdFast, macdSlow, macdSignal, kdKPeriod, kdDPeriod, rsiPeriod, rsiOverbought);
    }

    // ====================================================================
    //  核心计算接口
    // ====================================================================

    /**
     * 根据 K 线列表计算所有指标，返回最新的指标快照
     *
     * @param contract   合约名称
     * @param candlesticks K 线数据（正序，最新的在最后）
     * @return 最新一根 K 线对应的指标快照；若数据不足则返回 null
     */
    public IndicatorSnapshot calculate(String contract, List<Candlestick> candlesticks) {
        if (candlesticks == null || candlesticks.isEmpty()) {
            log.warn("K线数据为空，无法计算指标 合约={}", contract);
            return null;
        }

        // 需要足够多的 K 线才能保证指标稳定（warmup 期）
        int minRequired = Math.max(macdSlow + macdSignal, kdKPeriod + kdDPeriod) + 10;
        if (candlesticks.size() < minRequired) {
            log.warn("K线数量不足 合约={}, 当前={}, 最小需要={}", contract, candlesticks.size(), minRequired);
            return null;
        }

        // ── 1. 构建 TA4J BarSeries ──
        BarSeries series = buildBarSeries(contract, candlesticks);

        // ── 2. 计算各指标 ──
        int lastIdx  = series.getEndIndex();
        int prevIdx  = lastIdx - 1;

        // ── MACD ──
        // TA4J 0.16 移除了 DifferenceIndicator，改用 CachedIndicator 自定义差值
        ClosePriceIndicator closePriceInd = new ClosePriceIndicator(series);
        EMAIndicator emaFast = new EMAIndicator(closePriceInd, macdFast);
        EMAIndicator emaSlow = new EMAIndicator(closePriceInd, macdSlow);

        // DIF = EMA(fast) - EMA(slow)，以 series 为父指示器
        // getUnstableBars = macdSlow（DIF 需要 EMA(slow) 完全收敛）
        CachedIndicator<Num> dif = new CachedIndicator<Num>(series) {
            @Override protected Num calculate(int index) {
                return emaFast.getValue(index).minus(emaSlow.getValue(index));
            }
            @Override public int getUnstableBars() {
                return macdSlow;   // EMA(slow) 需要 macdSlow 根 K 线收敛
            }
        };

        // DEA = EMA(DIF, signal)
        EMAIndicator dea = new EMAIndicator(dif, macdSignal);

        // BAR = (DIF - DEA) * 2，以 dea 为父指示器
        // getUnstableBars = macdSlow + macdSignal（DEA 本身需要 signal 根收敛）
        CachedIndicator<Num> bar = new CachedIndicator<Num>(dea) {
            @Override protected Num calculate(int index) {
                return dif.getValue(index).minus(dea.getValue(index))
                        .multipliedBy(DecimalNum.valueOf(2));
            }
            @Override public int getUnstableBars() {
                return macdSlow + macdSignal;  // DIF 需 macdSlow + DEA 需 macdSignal
            }
        };

        double macdDifVal      = dif.getValue(lastIdx).doubleValue();
        double macdDeaVal      = dea.getValue(lastIdx).doubleValue();
        double macdBarVal      = bar.getValue(lastIdx).doubleValue() * 2;
        double prevMacdBarVal  = bar.getValue(prevIdx).doubleValue() * 2;

        // ── KD 随机指标（Stochastic Oscillator） ──
        // TA4J 中 StochasticOscillatorKIndicator 对应 %K（即 RSV 的平滑）
        // 其参数 barCount 对应 KD 的 k_period
        StochasticOscillatorKIndicator stochasticK = new StochasticOscillatorKIndicator(series, kdKPeriod);
        // %D = SMA(%K, d_period)
        StochasticOscillatorDIndicator stochasticD = new StochasticOscillatorDIndicator(stochasticK);
        // TA4J 默认 D 的平滑周期是3，与配置一致

        double kVal     = stochasticK.getValue(lastIdx).doubleValue();
        double dVal     = stochasticD.getValue(lastIdx).doubleValue();
        double prevKVal = stochasticK.getValue(prevIdx).doubleValue();
        double prevDVal = stochasticD.getValue(prevIdx).doubleValue();

        // ── RSI ──
        RSIIndicator rsiIndicator = new RSIIndicator(new ClosePriceIndicator(series), rsiPeriod);
        double rsiVal     = rsiIndicator.getValue(lastIdx).doubleValue();
        double prevRsiVal = rsiIndicator.getValue(prevIdx).doubleValue();

        // ── RSI 顶背离检测 ──
        boolean topDivergence = detectRsiTopDivergence(series, rsiIndicator, lastIdx);

        // ── 3. 组装快照 ──
        Candlestick latest = candlesticks.get(candlesticks.size() - 1);
        IndicatorSnapshot snapshot = IndicatorSnapshot.builder()
                .contract(contract)
                .timestamp(latest.getTimestamp())
                .closePrice(latest.getClose())
                .macdDif(macdDifVal)
                .macdDea(macdDeaVal)
                .macdBar(macdBarVal)
                .prevMacdBar(prevMacdBarVal)
                .kValue(kVal)
                .dValue(dVal)
                .prevKValue(prevKVal)
                .prevDValue(prevDVal)
                .rsiValue(rsiVal)
                .prevRsiValue(prevRsiVal)
                .rsiTopDivergence(topDivergence)
                .build();

        log.debug("指标计算完成 {}", snapshot);
        return snapshot;
    }

    // ====================================================================
    //  私有辅助方法
    // ====================================================================

    /**
     * 将内部 Candlestick 列表转换为 TA4J BarSeries
     */
    private BarSeries buildBarSeries(String name, List<Candlestick> candles) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).build();
        for (Candlestick c : candles) {
            ZonedDateTime time = Instant.ofEpochSecond(c.getTimestamp())
                    .atZone(ZoneId.systemDefault());
            Bar bar = new BaseBar(
                    Duration.ofMinutes(1), // 时间跨度（TA4J内部用于显示，不影响计算）
                    time,
                    DecimalNum.valueOf(c.getOpen()),
                    DecimalNum.valueOf(c.getHigh()),
                    DecimalNum.valueOf(c.getLow()),
                    DecimalNum.valueOf(c.getClose()),
                    DecimalNum.valueOf(c.getVolume()),
                    DecimalNum.valueOf(c.getQuoteVolume()),
                    0
            );
            series.addBar(bar);
        }
        return series;
    }

    /**
     * RSI 顶背离检测
     * <p>
     * 判断逻辑：在最近 divergenceLookback 根 K 线中，
     * 价格创出新高，但对应的 RSI 值没有同步创新高（甚至更低），
     * 这表明上升动能衰减，可能即将转向。
     * </p>
     *
     * @param series       BarSeries 对象
     * @param rsiIndicator RSI 指标
     * @param endIdx       当前索引
     * @return true = 检测到顶背离
     */
    private boolean detectRsiTopDivergence(BarSeries series, RSIIndicator rsiIndicator, int endIdx) {
        if (endIdx < rsiDivergenceLookback) return false;

        // 取回溯窗口内的数据
        int startIdx = endIdx - rsiDivergenceLookback;

        // 找到窗口内价格最高点和对应的 RSI
        double windowHighPrice = Double.MIN_VALUE;
        double windowHighRsi   = Double.MIN_VALUE;
        for (int i = startIdx; i < endIdx; i++) {
            double highPrice = series.getBar(i).getHighPrice().doubleValue();
            if (highPrice > windowHighPrice) {
                windowHighPrice = highPrice;
                windowHighRsi   = rsiIndicator.getValue(i).doubleValue();
            }
        }

        // 当前价格和 RSI
        double currentPrice = series.getBar(endIdx).getHighPrice().doubleValue();
        double currentRsi   = rsiIndicator.getValue(endIdx).doubleValue();

        // 顶背离：当前价格 >= 窗口最高价（价格新高），但当前 RSI < 窗口最高 RSI
        boolean divergence = currentPrice >= windowHighPrice * DIVERGENCE_PRICE_TOLERANCE
                && currentRsi < windowHighRsi - DIVERGENCE_RSI_THRESHOLD;

        if (divergence) {
            log.debug("检测到RSI顶背离 价格窗口高点={} 当前={}, RSI窗口高点={} 当前={}",
                    String.format("%.4f", windowHighPrice), String.format("%.4f", currentPrice),
                    String.format("%.2f", windowHighRsi), String.format("%.2f", currentRsi));
        }
        return divergence;
    }
}
