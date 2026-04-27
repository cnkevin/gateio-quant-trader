package com.quant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * K 线数据模型（蜡烛图）
 * <p>
 * 将 Gate.io SDK 返回的 {@link io.gate.gateapi.models.FuturesCandlestick}
 * 转换为内部使用的简洁 POJO，方便计算技术指标。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candlestick {

    /** 时间戳（Unix 秒） */
    private long timestamp;

    /** 开盘价 */
    private double open;

    /** 最高价 */
    private double high;

    /** 最低价 */
    private double low;

    /** 收盘价 */
    private double close;

    /** 成交量（合约张数） */
    private double volume;

    /** 成交额（USDT） */
    private double quoteVolume;

    // ====================================================================
    //  工厂方法：从 SDK 模型转换
    // ====================================================================

    /**
     * 从 Gate.io SDK 的 FuturesCandlestick 对象创建内部模型
     *
     * @param sdk Gate API 返回的 K 线对象
     * @return 内部 Candlestick 对象
     */
    public static Candlestick fromSdk(io.gate.gateapi.models.FuturesCandlestick sdk) {
        if (sdk == null) return null;
        return Candlestick.builder()
                // Gate.io 返回的 t 字段是 Long 类型（Unix 秒）
                .timestamp(sdk.getT() != null ? sdk.getT().longValue() : 0L)
                .open(parseDouble(sdk.getO()))
                .high(parseDouble(sdk.getH()))
                .low(parseDouble(sdk.getL()))
                .close(parseDouble(sdk.getC()))
                .volume(parseDouble(sdk.getV() != null ? String.valueOf(sdk.getV()) : null))
                .quoteVolume(parseDouble(sdk.getSum()))
                .build();
    }

    // ====================================================================
    //  工具方法
    // ====================================================================

    private static double parseDouble(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 将时间戳格式化为可读的本地时间字符串
     * 例如：2024-01-15 14:30:00
     */
    public String getFormattedTime() {
        return Instant.ofEpochSecond(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Override
    public String toString() {
        return String.format("Candlestick[%s | O=%.2f H=%.2f L=%.2f C=%.2f V=%.0f]",
                getFormattedTime(), open, high, low, close, volume);
    }
}
