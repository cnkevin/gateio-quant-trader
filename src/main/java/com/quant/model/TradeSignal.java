package com.quant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 交易信号枚举与交易信号数据模型
 */
public class TradeSignal {

    // ====================================================================
    //  信号类型枚举
    // ====================================================================

    /**
     * 交易信号类型
     */
    public enum SignalType {
        /** 无信号（观望） */
        NONE("无信号", "🔵"),
        /** 买入开仓信号 */
        BUY("买入开仓", "🟢"),
        /** 卖出平仓信号 */
        SELL("卖出平仓", "🔴"),
        /** 止损平仓信号 */
        STOP_LOSS("止损平仓", "🟠"),
        /** 止盈平仓信号 */
        TAKE_PROFIT("止盈平仓", "🟡");

        private final String label;
        private final String emoji;

        SignalType(String label, String emoji) {
            this.label = label;
            this.emoji = emoji;
        }

        public String getLabel() { return label; }
        public String getEmoji() { return emoji; }

        @Override
        public String toString() { return emoji + " " + label; }
    }

    // ====================================================================
    //  信号数据模型
    // ====================================================================

    /**
     * 一个完整的交易信号，包含信号类型和触发原因说明
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Signal {

        /** 信号类型 */
        private SignalType type;

        /** 合约名称 */
        private String contract;

        /** 触发时的收盘价 */
        private double price;

        /** 信号触发时间（Unix 秒） */
        private long timestamp;

        /** 触发原因的详细描述（用于日志和回测报告） */
        private String reason;

        /** 触发时的指标快照（方便调试） */
        private IndicatorSnapshot snapshot;

        /**
         * 判断是否需要执行操作（非 NONE 信号）
         */
        public boolean isActionable() {
            return type != SignalType.NONE;
        }

        /**
         * 判断是否为开仓信号
         */
        public boolean isEntrySignal() {
            return type == SignalType.BUY;
        }

        /**
         * 判断是否为平仓信号（任何方式的平仓）
         */
        public boolean isExitSignal() {
            return type == SignalType.SELL
                    || type == SignalType.STOP_LOSS
                    || type == SignalType.TAKE_PROFIT;
        }

        /** 获取格式化时间字符串 */
        public String getFormattedTime() {
            return Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        @Override
        public String toString() {
            return String.format("%s %s 价格=%.4f 时间=%s 原因=%s",
                    type.getEmoji(), contract, price, getFormattedTime(), reason);
        }
    }
}
