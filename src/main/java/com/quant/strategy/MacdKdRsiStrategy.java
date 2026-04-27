package com.quant.strategy;

import com.quant.config.AppConfig;
import com.quant.model.IndicatorSnapshot;
import com.quant.model.TradeSignal;
import com.quant.model.TradeSignal.Signal;
import com.quant.model.TradeSignal.SignalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MACD + KD + RSI 复合策略信号生成器
 * <p>
 * 策略逻辑：
 * <ul>
 *   <li><b>买入开仓：</b>
 *     MACD DIF 在 0 轴上方（多头格局） AND
 *     KD 指标在超卖区（≤20轴）发生金叉，并且 K 值上穿 20 轴
 *   </li>
 *   <li><b>卖出平仓（策略信号）：</b>
 *     RSI 出现顶背离（价格创新高但 RSI 没有创新高）之后，
 *     RSI 跌破超买线（70）时立即平仓
 *   </li>
 *   <li><b>止损平仓：</b>
 *     当前价格低于开仓价格 × (1 - stopLossPct) 时触发
 *   </li>
 *   <li><b>止盈平仓：</b>
 *     当前价格高于开仓价格 × (1 + takeProfitPct) 时触发
 *   </li>
 * </ul>
 * </p>
 *
 * <p>持仓模式：单向持多仓（不做空）</p>
 */
public class MacdKdRsiStrategy {

    private static final Logger log = LoggerFactory.getLogger(MacdKdRsiStrategy.class);

    /** 用于写入交易信号专用日志文件（在 logback.xml 中配置） */
    private static final Logger tradeLog = LoggerFactory.getLogger("TRADE_SIGNAL");

    private final AppConfig.IndicatorConfig.KdConfig  kdCfg;
    private final AppConfig.IndicatorConfig.RsiConfig rsiCfg;
    private final AppConfig.TradingConfig tradingCfg;

    /** 是否已观察到 RSI 顶背离（等待 RSI 跌破 70 来触发平仓） */
    private volatile boolean rsiDivergenceObserved = false;

    public MacdKdRsiStrategy() {
        AppConfig cfg   = AppConfig.getInstance();
        this.kdCfg      = cfg.getIndicator().getKd();
        this.rsiCfg     = cfg.getIndicator().getRsi();
        this.tradingCfg = cfg.getTrading();
    }

    // ====================================================================
    //  开仓信号判断
    // ====================================================================

    /**
     * 根据指标快照判断是否产生开仓信号
     * 仅在当前无持仓时调用此方法
     *
     * @param snapshot 最新的指标快照
     * @return 交易信号（BUY 或 NONE）
     */
    public Signal checkEntrySignal(IndicatorSnapshot snapshot) {
        if (snapshot == null) return noneSignal(null, 0, "指标数据为空");

        String contract = snapshot.getContract();
        double price    = snapshot.getClosePrice();
        long   ts       = snapshot.getTimestamp();

        // ── 条件1：MACD DIF 在 0 轴上方 ──
        if (!snapshot.isMacdAboveZero()) {
            log.debug("[{}] 无买入信号 - MACD DIF({}) 在0轴下方",
                    contract, String.format("%.4f", snapshot.getMacdDif()));
            return noneSignal(contract, ts, "MACD DIF 在0轴下方");
        }

        // ── 条件2：KD 超卖区金叉并上穿 20 轴 ──
        if (!snapshot.isKdOversoldGoldenCross(kdCfg.getOversoldLevel())) {
            log.debug("[{}] 无买入信号 - KD 未满足超卖金叉条件 K={} D={} prevK={} prevD={}",
                    contract,
                    String.format("%.2f", snapshot.getKValue()),
                    String.format("%.2f", snapshot.getDValue()),
                    String.format("%.2f", snapshot.getPrevKValue()),
                    String.format("%.2f", snapshot.getPrevDValue()));
            return noneSignal(contract, ts, "KD 未出现超卖区金叉");
        }

        // ── 两个条件均满足，生成买入信号 ──
        String reason = String.format(
                "买入开仓信号: MACD DIF=%.4f(>0轴) | K=%.2f 上穿 D=%.2f 并上穿 %d轴",
                snapshot.getMacdDif(),
                snapshot.getKValue(),
                snapshot.getDValue(),
                kdCfg.getOversoldLevel()
        );

        log.info("🟢 [{}] {} | 价格={}", contract, reason, price);
        tradeLog.info("BUY | {} | 价格={} | {}", contract, price, reason);

        return Signal.builder()
                .type(SignalType.BUY)
                .contract(contract)
                .price(price)
                .timestamp(ts)
                .reason(reason)
                .snapshot(snapshot)
                .build();
    }

    // ====================================================================
    //  平仓信号判断
    // ====================================================================

    /**
     * 根据指标快照和当前持仓信息判断是否产生平仓信号
     * 仅在当前有持仓时调用此方法
     *
     * @param snapshot  最新的指标快照
     * @param entryPrice 开仓价格（用于止损止盈计算）
     * @return 交易信号（SELL / STOP_LOSS / TAKE_PROFIT / NONE）
     */
    public Signal checkExitSignal(IndicatorSnapshot snapshot, double entryPrice) {
        if (snapshot == null) return noneSignal(null, 0, "指标数据为空");

        String contract = snapshot.getContract();
        double price    = snapshot.getClosePrice();
        long   ts       = snapshot.getTimestamp();

        // ── 优先级1：止损检查（风控，最高优先级） ──
        double stopLossPrice = entryPrice * (1 - tradingCfg.getStopLossPct());
        if (price <= stopLossPrice) {
            String reason = String.format("止损触发: 当前价=%.4f ≤ 止损价=%.4f (开仓价=%.4f, 止损比例=%.1f%%)",
                    price, stopLossPrice, entryPrice, tradingCfg.getStopLossPct() * 100);
            log.warn("🟠 [{}] {} ", contract, reason);
            tradeLog.info("STOP_LOSS | {} | 价格={} | {}", contract, price, reason);
            resetDivergenceState();
            return Signal.builder()
                    .type(SignalType.STOP_LOSS).contract(contract)
                    .price(price).timestamp(ts).reason(reason).snapshot(snapshot).build();
        }

        // ── 优先级2：止盈检查 ──
        double takeProfitPrice = entryPrice * (1 + tradingCfg.getTakeProfitPct());
        if (price >= takeProfitPrice) {
            String reason = String.format("止盈触发: 当前价=%.4f ≥ 止盈价=%.4f (开仓价=%.4f, 止盈比例=%.1f%%)",
                    price, takeProfitPrice, entryPrice, tradingCfg.getTakeProfitPct() * 100);
            log.info("🟡 [{}] {}", contract, reason);
            tradeLog.info("TAKE_PROFIT | {} | 价格={} | {}", contract, price, reason);
            resetDivergenceState();
            return Signal.builder()
                    .type(SignalType.TAKE_PROFIT).contract(contract)
                    .price(price).timestamp(ts).reason(reason).snapshot(snapshot).build();
        }

        // ── 优先级3：RSI 顶背离后跌破超买线 ──
        // 步骤3a：更新背离观察状态
        if (snapshot.isRsiTopDivergence()) {
            if (!rsiDivergenceObserved) {
                rsiDivergenceObserved = true;
                log.info("[{}] 检测到 RSI 顶背离，等待 RSI 跌破 {} 触发平仓，当前RSI={}",
                        contract, rsiCfg.getOverboughtLevel(),
                        String.format("%.2f", snapshot.getRsiValue()));
            }
        }

        // 步骤3b：已观察到顶背离，检查 RSI 是否跌破超买线
        if (rsiDivergenceObserved
                && snapshot.isRsiBreakBelowOverbought(rsiCfg.getOverboughtLevel())) {

            String reason = String.format(
                    "RSI策略平仓: 顶背离后RSI从%.2f跌破%d线至%.2f",
                    snapshot.getPrevRsiValue(),
                    rsiCfg.getOverboughtLevel(),
                    snapshot.getRsiValue()
            );
            log.info("🔴 [{}] {} | 价格={}", contract, reason, price);
            tradeLog.info("SELL | {} | 价格={} | {}", contract, price, reason);
            resetDivergenceState();
            return Signal.builder()
                    .type(SignalType.SELL).contract(contract)
                    .price(price).timestamp(ts).reason(reason).snapshot(snapshot).build();
        }

        log.debug("[{}] 持仓中，无平仓信号 价格={} RSI={} 背离观察={}",
                contract, price,
                String.format("%.2f", snapshot.getRsiValue()),
                rsiDivergenceObserved);
        return noneSignal(contract, ts, "无平仓条件");
    }

    // ====================================================================
    //  状态管理
    // ====================================================================

    /**
     * 重置背离观察状态（平仓后调用）
     */
    public void resetDivergenceState() {
        rsiDivergenceObserved = false;
        log.debug("背离状态已重置");
    }

    // ====================================================================
    //  工具方法
    // ====================================================================

    private Signal noneSignal(String contract, long ts, String reason) {
        return Signal.builder()
                .type(SignalType.NONE)
                .contract(contract)
                .price(0)
                .timestamp(ts)
                .reason(reason)
                .build();
    }
}
