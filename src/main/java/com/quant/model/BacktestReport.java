package com.quant.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 回测报告实体类
 */
public class BacktestReport {

    private Long id;
    private String reportId;
    private String contract;
    private String strategyName;

    // 回测参数
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String klineInterval;
    private BigDecimal initialCapital;
    private BigDecimal commissionRate;

    // 回测结果
    private BigDecimal finalCapital;
    private BigDecimal totalReturn;
    private Integer totalTrades;
    private Integer winTrades;
    private Integer lossTrades;
    private BigDecimal winRate;
    private BigDecimal maxDrawdown;
    private BigDecimal sharpeRatio;

    private LocalDateTime createdAt;

    // 构造方法
    public BacktestReport() {
    }

    public BacktestReport(String reportId, String contract, String strategyName) {
        this.reportId = reportId;
        this.contract = contract;
        this.strategyName = strategyName;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
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

    @Override
    public String toString() {
        if (reportId == null || contract == null) return "BacktestReport{incomplete}";
        double retVal = totalReturn != null ? totalReturn.doubleValue() * 100 : 0;
        double winVal = winRate != null ? winRate.doubleValue() * 100 : 0;
        double ddVal  = maxDrawdown != null ? maxDrawdown.doubleValue() * 100 : 0;
        return String.format(
                "BacktestReport{reportId='%s', contract='%s', totalReturn=%.2f%%, winRate=%.2f%%, maxDrawdown=%.2f%%}",
                reportId, contract, retVal, winVal, ddVal);
    }
}
