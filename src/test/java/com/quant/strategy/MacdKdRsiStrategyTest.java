package com.quant.strategy;

import com.quant.config.AppConfig;
import com.quant.model.IndicatorSnapshot;
import com.quant.model.TradeSignal.Signal;
import com.quant.model.TradeSignal.SignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 策略信号生成器单元测试
 * <p>
 * 验证各种条件组合下信号是否按预期生成
 * </p>
 */
class MacdKdRsiStrategyTest {

    private MacdKdRsiStrategy strategy;

    @BeforeEach
    void setUp() {
        // 使用默认配置初始化
        AppConfig.load();
        strategy = new MacdKdRsiStrategy();
    }

    // ── 买入信号测试 ──────────────────────────────────────────────────

    /**
     * 测试：MACD 在0轴上方 + KD 超卖金叉 → 应产生 BUY 信号
     */
    @Test
    void testBuySignal_whenMacdAboveZeroAndKdOversoldGoldenCross() {
        IndicatorSnapshot snapshot = IndicatorSnapshot.builder()
                .contract("BTC_USDT")
                .timestamp(System.currentTimeMillis() / 1000)
                .closePrice(50000.0)
                // MACD DIF 在 0 轴上方
                .macdDif(100.0)
                .macdDea(80.0)
                .macdBar(40.0)
                .prevMacdBar(30.0)
                // KD 前一根在 20 以下，当前金叉并上穿 20
                .prevKValue(15.0)
                .prevDValue(18.0)
                .kValue(22.0)
                .dValue(19.0)
                // RSI 正常
                .rsiValue(45.0)
                .prevRsiValue(43.0)
                .rsiTopDivergence(false)
                .build();

        Signal signal = strategy.checkEntrySignal(snapshot);

        assertEquals(SignalType.BUY, signal.getType(),
                "MACD在0轴上方且KD超卖金叉时，应产生BUY信号");
        assertTrue(signal.isEntrySignal());
        System.out.println("买入信号触发原因: " + signal.getReason());
    }

    /**
     * 测试：MACD 在0轴下方 → 不应产生 BUY 信号
     */
    @Test
    void testNoBuySignal_whenMacdBelowZero() {
        IndicatorSnapshot snapshot = IndicatorSnapshot.builder()
                .contract("BTC_USDT")
                .timestamp(System.currentTimeMillis() / 1000)
                .closePrice(50000.0)
                // MACD DIF 在 0 轴下方
                .macdDif(-50.0)
                .macdDea(-30.0)
                .macdBar(-40.0)
                .prevMacdBar(-50.0)
                .prevKValue(15.0).prevDValue(18.0)
                .kValue(22.0).dValue(19.0)
                .rsiValue(45.0).prevRsiValue(43.0)
                .rsiTopDivergence(false)
                .build();

        Signal signal = strategy.checkEntrySignal(snapshot);

        assertEquals(SignalType.NONE, signal.getType(),
                "MACD DIF 在0轴下方时，不应产生买入信号");
    }

    // ── 卖出信号测试 ──────────────────────────────────────────────────

    /**
     * 测试：止损触发
     */
    @Test
    void testStopLoss_whenPriceBelowStopLevel() {
        double entryPrice = 50000.0;
        // 当前价格低于止损线（5% 止损，止损价 = 47500）
        double currentPrice = 47000.0;

        IndicatorSnapshot snapshot = IndicatorSnapshot.builder()
                .contract("BTC_USDT")
                .timestamp(System.currentTimeMillis() / 1000)
                .closePrice(currentPrice)
                .macdDif(10.0).macdDea(5.0).macdBar(10.0).prevMacdBar(5.0)
                .kValue(30.0).dValue(32.0).prevKValue(28.0).prevDValue(33.0)
                .rsiValue(35.0).prevRsiValue(40.0)
                .rsiTopDivergence(false)
                .build();

        Signal signal = strategy.checkExitSignal(snapshot, entryPrice);

        assertEquals(SignalType.STOP_LOSS, signal.getType(),
                "价格低于止损线时应触发止损");
        System.out.println("止损信号原因: " + signal.getReason());
    }

    /**
     * 测试：止盈触发
     */
    @Test
    void testTakeProfit_whenPriceAboveTakeProfit() {
        double entryPrice = 50000.0;
        // 10% 止盈，止盈价 = 55000
        double currentPrice = 56000.0;

        IndicatorSnapshot snapshot = IndicatorSnapshot.builder()
                .contract("BTC_USDT")
                .timestamp(System.currentTimeMillis() / 1000)
                .closePrice(currentPrice)
                .macdDif(10.0).macdDea(5.0).macdBar(10.0).prevMacdBar(5.0)
                .kValue(70.0).dValue(65.0).prevKValue(68.0).prevDValue(64.0)
                .rsiValue(75.0).prevRsiValue(72.0)
                .rsiTopDivergence(false)
                .build();

        Signal signal = strategy.checkExitSignal(snapshot, entryPrice);

        assertEquals(SignalType.TAKE_PROFIT, signal.getType(),
                "价格超过止盈线时应触发止盈");
    }

    /**
     * 测试：RSI 顶背离后跌破 70 → 产生 SELL 信号
     */
    @Test
    void testSellSignal_afterRsiDivergenceBreaksBelow70() {
        double entryPrice = 50000.0;
        double currentPrice = 54000.0; // 未触及止盈（需高于 55000）

        // 先触发顶背离
        IndicatorSnapshot divergenceSnapshot = IndicatorSnapshot.builder()
                .contract("BTC_USDT")
                .timestamp(System.currentTimeMillis() / 1000)
                .closePrice(currentPrice)
                .macdDif(10.0).macdDea(5.0).macdBar(10.0).prevMacdBar(5.0)
                .kValue(70.0).dValue(65.0).prevKValue(68.0).prevDValue(64.0)
                .rsiValue(72.0).prevRsiValue(70.0)
                .rsiTopDivergence(true)  // 有顶背离
                .build();
        strategy.checkExitSignal(divergenceSnapshot, entryPrice); // 触发背离状态记录

        // 然后 RSI 跌破 70
        IndicatorSnapshot breakSnapshot = IndicatorSnapshot.builder()
                .contract("BTC_USDT")
                .timestamp(System.currentTimeMillis() / 1000)
                .closePrice(currentPrice)
                .macdDif(10.0).macdDea(5.0).macdBar(10.0).prevMacdBar(5.0)
                .kValue(65.0).dValue(62.0).prevKValue(68.0).prevDValue(64.0)
                .rsiValue(68.0)     // 当前 RSI 跌破 70
                .prevRsiValue(71.0) // 上一根 RSI 在 70 上方
                .rsiTopDivergence(false)
                .build();

        Signal signal = strategy.checkExitSignal(breakSnapshot, entryPrice);

        assertEquals(SignalType.SELL, signal.getType(),
                "RSI顶背离后跌破70时应产生SELL信号");
        System.out.println("卖出信号原因: " + signal.getReason());
    }

    /**
     * 测试：IndicatorSnapshot 辅助方法正确性
     */
    @Test
    void testIndicatorSnapshotHelpers() {
        IndicatorSnapshot s = IndicatorSnapshot.builder()
                .macdDif(5.0)
                .prevKValue(15.0).prevDValue(18.0)
                .kValue(22.0).dValue(19.0)
                .rsiValue(68.0).prevRsiValue(72.0)
                .build();

        assertTrue(s.isMacdAboveZero(),         "DIF=5>0，应返回true");
        assertTrue(s.isKdGoldenCross(),          "K从15穿越D从18，现K=22>D=19，应为金叉");
        assertTrue(s.isKdOversoldGoldenCross(20),"金叉发生在超卖区附近且上穿20轴");
        assertTrue(s.isRsiBreakBelowOverbought(70), "RSI从72跌至68，应为跌破70");
    }
}
