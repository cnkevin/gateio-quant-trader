package com.quant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 技术指标快照
 * <p>
 * 存储某一时刻所有指标的计算结果，是策略判断的数据来源。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorSnapshot {

    // ──────────────────────────────────────────────────────
    //  K 线基础数据
    // ──────────────────────────────────────────────────────

    /** 合约名称（如 BTC_USDT） */
    private String contract;

    /** 当前 K 线时间戳（Unix 秒） */
    private long timestamp;

    /** 当前收盘价 */
    private double closePrice;

    // ──────────────────────────────────────────────────────
    //  MACD 指标值
    // ──────────────────────────────────────────────────────

    /** MACD 值（DIF：快线EMA - 慢线EMA） */
    private double macdDif;

    /** MACD 信号线（DEA：DIF的EMA） */
    private double macdDea;

    /** MACD 柱（BAR：(DIF - DEA) * 2） */
    private double macdBar;

    /** 上一根 K 线的 MACD 柱值（用于金叉/死叉判断） */
    private double prevMacdBar;

    // ──────────────────────────────────────────────────────
    //  KD 随机指标值
    // ──────────────────────────────────────────────────────

    /** K 值（当前） */
    private double kValue;

    /** D 值（当前） */
    private double dValue;

    /** K 值（前一根 K 线） */
    private double prevKValue;

    /** D 值（前一根 K 线） */
    private double prevDValue;

    // ──────────────────────────────────────────────────────
    //  RSI 指标值
    // ──────────────────────────────────────────────────────

    /** 当前 RSI 值 */
    private double rsiValue;

    /** 上一根 K 线的 RSI 值 */
    private double prevRsiValue;

    /** 是否检测到 RSI 顶背离（价格创新高但 RSI 没有创新高） */
    private boolean rsiTopDivergence;

    // ====================================================================
    //  信号判断辅助方法
    // ====================================================================

    /**
     * 判断 MACD DIF 线是否在 0 轴上方
     *
     * @return true = DIF > 0（MACD 快线在 0 轴上方，代表多头主导）
     */
    public boolean isMacdAboveZero() {
        return macdDif > 0;
    }

    /**
     * 判断 KD 金叉
     * 条件：K 上穿 D（前一根 K 在 D 下方，当前 K 在 D 上方）
     *
     * @return true = KD 金叉
     */
    public boolean isKdGoldenCross() {
        return prevKValue < prevDValue && kValue >= dValue;
    }

    /**
     * 判断 KD 金叉是否发生在超卖区（20 轴）附近
     * 策略要求：金叉时 K、D 值均曾在 20 以下，且金叉后上穿 20 轴
     *
     * @param oversoldLevel 超卖线数值（通常为 20）
     * @return true = 符合策略的超卖区金叉
     */
    public boolean isKdOversoldGoldenCross(int oversoldLevel) {
        // 前一根 K 线的 K/D 均在超卖线附近或以下（至少有一个在线以下）
        boolean wasOversold = prevKValue <= oversoldLevel || prevDValue <= oversoldLevel;
        // 当前已经上穿超卖线
        boolean nowAboveOversold = kValue > oversoldLevel;
        return wasOversold && nowAboveOversold && isKdGoldenCross();
    }

    /**
     * 判断 RSI 跌破超买线（平仓信号触发）
     * 条件：上一根 RSI >= 超买线，当前 RSI < 超买线
     *
     * @param overboughtLevel 超买线（通常为 70）
     * @return true = RSI 从超买区跌破超买线
     */
    public boolean isRsiBreakBelowOverbought(int overboughtLevel) {
        return prevRsiValue >= overboughtLevel && rsiValue < overboughtLevel;
    }

    @Override
    public String toString() {
        return String.format(
                "指标快照[%s] 价格=%.4f | MACD(DIF=%.4f, DEA=%.4f, BAR=%.4f) | "
                        + "KD(K=%.2f, D=%.2f) | RSI=%.2f | 顶背离=%s",
                contract, closePrice,
                macdDif, macdDea, macdBar,
                kValue, dValue, rsiValue,
                rsiTopDivergence ? "是" : "否"
        );
    }
}
