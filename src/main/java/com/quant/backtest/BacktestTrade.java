package com.quant.backtest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单笔回测交易记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestTrade {

    /** 开仓时间（Unix 秒） */
    private long entryTime;

    /** 平仓时间（Unix 秒） */
    private long exitTime;

    /** 开仓价格 */
    private double entryPrice;

    /** 平仓价格 */
    private double exitPrice;

    /** 开仓张数 */
    private long size;

    /** 平仓类型（SELL / STOP_LOSS / TAKE_PROFIT） */
    private String exitType;

    /** 本笔收益（USDT，未扣除手续费） */
    private double pnlGross;

    /** 手续费（USDT） */
    private double commission;

    /** 本笔净收益（USDT） */
    private double pnlNet;

    /** 收益率（%） */
    private double returnPct;

    /**
     * 格式化输出
     */
    @Override
    public String toString() {
        return String.format(
                "  开仓: %s @ %.4f | 平仓: %s @ %.4f | 类型=%-12s | 净盈亏=%.2f USDT (%.2f%%)",
                formatTime(entryTime), entryPrice,
                formatTime(exitTime), exitPrice,
                exitType,
                pnlNet, returnPct
        );
    }

    private String formatTime(long epochSec) {
        return java.time.Instant.ofEpochSecond(epochSec)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }
}
