package com.quant.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实盘交易记录实体类
 */
public class TradingRecord {

    private Long id;
    private String orderId;
    private String contract;

    // 交易信息
    private String signalType;
    private String signalReason;
    private String direction;
    private BigDecimal size;
    private BigDecimal price;
    private String orderType;

    // 状态
    private String status;
    private String errorMessage;

    // 盈亏 (平仓时填写)
    private BigDecimal pnl;
    private BigDecimal commission;

    // 时间戳
    private LocalDateTime signalTime;
    private LocalDateTime orderTime;
    private LocalDateTime fillTime;
    private LocalDateTime createdAt;

    // 状态常量
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    // 方向常量
    public static final String DIR_OPEN_LONG = "OPEN_LONG";
    public static final String DIR_CLOSE_LONG = "CLOSE_LONG";
    public static final String DIR_OPEN_SHORT = "OPEN_SHORT";
    public static final String DIR_CLOSE_SHORT = "CLOSE_SHORT";

    // 构造方法
    public TradingRecord() {
        this.status = STATUS_PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public TradingRecord(String orderId, String contract, String signalType, String direction) {
        this();
        this.orderId = orderId;
        this.contract = contract;
        this.signalType = signalType;
        this.direction = direction;
        this.signalTime = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getContract() {
        return contract;
    }

    public void setContract(String contract) {
        this.contract = contract;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public String getSignalReason() {
        return signalReason;
    }

    public void setSignalReason(String signalReason) {
        this.signalReason = signalReason;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public BigDecimal getSize() {
        return size;
    }

    public void setSize(BigDecimal size) {
        this.size = size;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public BigDecimal getPnl() {
        return pnl;
    }

    public void setPnl(BigDecimal pnl) {
        this.pnl = pnl;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    public LocalDateTime getSignalTime() {
        return signalTime;
    }

    public void setSignalTime(LocalDateTime signalTime) {
        this.signalTime = signalTime;
    }

    public LocalDateTime getOrderTime() {
        return orderTime;
    }

    public void setOrderTime(LocalDateTime orderTime) {
        this.orderTime = orderTime;
    }

    public LocalDateTime getFillTime() {
        return fillTime;
    }

    public void setFillTime(LocalDateTime fillTime) {
        this.fillTime = fillTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return String.format(
                "TradingRecord{orderId='%s', contract='%s', direction='%s', size=%s, price=%s, status='%s'}",
                orderId, contract, direction, size, price, status);
    }
}
