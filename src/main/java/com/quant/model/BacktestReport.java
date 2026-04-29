package com.quant.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 回测报告实体类
 * <p>
 * 存储一次完整回测的统计结果，包括：
 * <ul>
 *   <li>回测参数（时间范围、K线周期、手续费率等）</li>
 *   <li>资金变化（初始资金、最终资金、总收益率）</li>
 *   <li>交易统计（总交易次数、盈利次数、亏损次数、胜率）</li>
 *   <li>风险指标（最大回撤、夏普比率等）</li>
 * </ul>
 * </p>
 *
 * <h3>收益率计算说明：</h3>
 * <pre>
 * 总收益率 = (最终资金 - 初始资金) / 初始资金
 * 胜率 = 盈利交易次数 / 总交易次数
 * </pre>
 *
 * <h3>最大回撤计算说明：</h3>
 * <pre>
 * 最大回撤 = max(Peak - Trough) / Peak × 100%
 * 其中 Peak 为历史最高权益，Trough 为之后的最低权益
 * </pre>
 *
 * @author Quant Trader
 * @version 1.0.0
 * @see BacktestTrade
 */
public class BacktestReport {

    /** 数据库自增主键 */
    private Long id;

    /** 回测报告唯一标识（8位UUID） */
    private String reportId;

    /** 合约名称（如 BTC_USDT） */
    private String contract;

    /** 策略名称 */
    private String strategyName;

    // ==================== 回测参数 ====================

    /** 回测开始时间 */
    private LocalDateTime startTime;

    /** 回测结束时间 */
    private LocalDateTime endTime;

    /** K线周期（如 15m, 1h） */
    private String klineInterval;

    /** 初始资金（USDT） */
    private BigDecimal initialCapital;

    /** 手续费率 */
    private BigDecimal commissionRate;

    // ==================== 回测结果 ====================

    /** 最终资金（USDT） */
    private BigDecimal finalCapital;

    /** 总收益率（小数形式，如 0.15 表示 15%） */
    private BigDecimal totalReturn;

    /** 总交易次数 */
    private Integer totalTrades;

    /** 盈利交易次数 */
    private Integer winTrades;

    /** 亏损交易次数 */
    private Integer lossTrades;

    /** 胜率（小数形式，如 0.6 表示 60%） */
    private BigDecimal winRate;

    /** 最大回撤（小数形式，如 0.1 表示 10%） */
    private BigDecimal maxDrawdown;

    /** 夏普比率（暂未实现，固定为 0） */
    private BigDecimal sharpeRatio;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    // ==================== 构造方法 ====================

    /**
     * 默认构造函数
     */
    public BacktestReport() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 构造函数
     *
     * @param reportId     报告ID
     * @param contract     合约名称
     * @param strategyName 策略名称
     */
    public BacktestReport(String reportId, String contract, String strategyName) {
        this();
        this.reportId = reportId;
        this.contract = contract;
        this.strategyName = strategyName;
    }

    // ==================== Getter / Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getContract() {
        return contract;
    }

    public void setContract(String contract) {
        this.contract = contract;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getKlineInterval() {
        return klineInterval;
    }

    public void setKlineInterval(String klineInterval) {
        this.klineInterval = klineInterval;
    }

    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    public void setInitialCapital(BigDecimal initialCapital) {
        this.initialCapital = initialCapital;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
    }

    public BigDecimal getFinalCapital() {
        return finalCapital;
    }

    public void setFinalCapital(BigDecimal finalCapital) {
        this.finalCapital = finalCapital;
    }

    public BigDecimal getTotalReturn() {
        return totalReturn;
    }

    public void setTotalReturn(BigDecimal totalReturn) {
        this.totalReturn = totalReturn;
    }

    public Integer getTotalTrades() {
        return totalTrades;
    }

    public void setTotalTrades(Integer totalTrades) {
        this.totalTrades = totalTrades;
    }

    public Integer getWinTrades() {
        return winTrades;
    }

    public void setWinTrades(Integer winTrades) {
        this.winTrades = winTrades;
    }

    public Integer getLossTrades() {
        return lossTrades;
    }

    public void setLossTrades(Integer lossTrades) {
        this.lossTrades = lossTrades;
    }

    public BigDecimal getWinRate() {
        return winRate;
    }

    public void setWinRate(BigDecimal winRate) {
        this.winRate = winRate;
    }

    public BigDecimal getMaxDrawdown() {
        return maxDrawdown;
    }

    public void setMaxDrawdown(BigDecimal maxDrawdown) {
        this.maxDrawdown = maxDrawdown;
    }

    public BigDecimal getSharpeRatio() {
        return sharpeRatio;
    }

    public void setSharpeRatio(BigDecimal sharpeRatio) {
        this.sharpeRatio = sharpeRatio;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取总收益率（百分比形式）
     *
     * @return 收益率百分比，如 15.5 表示 15.5%
     */
    public double getTotalReturnPercent() {
        return totalReturn != null ? totalReturn.doubleValue() * 100 : 0;
    }

    /**
     * 获取胜率（百分比形式）
     *
     * @return 胜率百分比，如 60.0 表示 60%
     */
    public double getWinRatePercent() {
        return winRate != null ? winRate.doubleValue() * 100 : 0;
    }

    /**
     * 获取最大回撤（百分比形式）
     *
     * @return 回撤百分比，如 8.5 表示 8.5%
     */
    public double getMaxDrawdownPercent() {
        return maxDrawdown != null ? maxDrawdown.doubleValue() * 100 : 0;
    }

    /**
     * 获取总盈亏金额
     *
     * @return 最终资金 - 初始资金
     */
    public BigDecimal getTotalPnl() {
        if (finalCapital == null || initialCapital == null) {
            return BigDecimal.ZERO;
        }
        return finalCapital.subtract(initialCapital);
    }

    /**
     * 获取格式化的时间范围
     *
     * @return 格式：yyyy-MM-dd HH:mm ~ yyyy-MM-dd HH:mm
     */
    public String getFormattedTimeRange() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String start = startTime != null ? startTime.format(fmt) : "N/A";
        String end = endTime != null ? endTime.format(fmt) : "N/A";
        return start + " ~ " + end;
    }

    @Override
    public String toString() {
        if (reportId == null || contract == null) {
            return "BacktestReport{incomplete}";
        }
        return String.format(
                "BacktestReport{reportId='%s', contract='%s', totalReturn=%.2f%%, winRate=%.2f%%, maxDrawdown=%.2f%%}",
                reportId, contract, getTotalReturnPercent(), getWinRatePercent(), getMaxDrawdownPercent());
    }
}
