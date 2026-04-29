package com.quant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 单笔回测交易记录模型
 * <p>
 * 记录回测中每笔完整交易（开仓→平仓）的详细信息，
 * 包括开仓/平仓价格、时间、盈亏计算等。
 * </p>
 *
 * <h3>字段说明：</h3>
 * <ul>
 *   <li>entryTime / exitTime：Unix 时间戳（秒），存储和比较更高效</li>
 *   <li>pnlGross：毛收益 = (平仓价 - 开仓价) × 张数</li>
 *   <li>commission：手续费 = (开仓价 + 平仓价) × 张数 × 手续费率</li>
 *   <li>pnlNet：净收益 = 毛收益 - 手续费</li>
 * </ul>
 *
 * @author Quant Trader
 * @version 1.0.0
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

    /** 开仓价格（成交均价） */
    private double entryPrice;

    /** 平仓价格（成交均价） */
    private double exitPrice;

    /** 开仓张数（合约数量） */
    private long size;

    /** 平仓类型（SELL=策略平仓 / STOP_LOSS=止损 / TAKE_PROFIT=止盈） */
    private String exitType;

    /** 毛收益（USDT，未扣除手续费） */
    private double pnlGross;

    /** 手续费（USDT） */
    private double commission;

    /** 净收益（USDT，已扣除手续费） */
    private double pnlNet;

    /** 收益率（%，相对于保证金） */
    private double returnPct;

    /**
     * 获取格式化后的开仓时间
     *
     * @return 格式：MM-dd HH:mm，例如 01-15 14:30
     */
    public String getFormattedEntryTime() {
        return formatTime(entryTime);
    }

    /**
     * 获取格式化后的平仓时间
     *
     * @return 格式：MM-dd HH:mm，例如 01-15 16:45
     */
    public String getFormattedExitTime() {
        return formatTime(exitTime);
    }

    /**
     * 格式化时间戳为本地时间字符串
     *
     * @param epochSec Unix 时间戳（秒）
     * @return 格式化的日期时间字符串
     */
    private String formatTime(long epochSec) {
        return Instant.ofEpochSecond(epochSec)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    /**
     * 判断是否为盈利交易
     *
     * @return true = 盈利，false = 亏损
     */
    public boolean isProfitable() {
        return pnlNet > 0;
    }

    /**
     * 判断是否为止损平仓
     *
     * @return true = 止损平仓
     */
    public boolean isStopLoss() {
        return "STOP_LOSS".equals(exitType);
    }

    /**
     * 判断是否为止盈平仓
     *
     * @return true = 止盈平仓
     */
    public boolean isTakeProfit() {
        return "TAKE_PROFIT".equals(exitType);
    }

    /**
     * 获取持仓时长（分钟）
     *
     * @return 持仓时长（分钟）
     */
    public long getHoldingMinutes() {
        return (exitTime - entryTime) / 60;
    }

    @Override
    public String toString() {
        return String.format(
                "  开仓: %s @ %.4f | 平仓: %s @ %.4f | 类型=%-12s | 净盈亏=%.2f USDT (%.2f%%)",
                getFormattedEntryTime(), entryPrice,
                getFormattedExitTime(), exitPrice,
                exitType,
                pnlNet, returnPct
        );
    }
}
