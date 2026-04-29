package com.quant.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 实盘交易记录实体类
 * <p>
 * 记录实盘交易的每一笔订单信息，包括：
 * <ul>
 *   <li>订单基本信息（订单ID、合约、信号类型）</li>
 *   <li>成交信息（张数、价格、订单类型）</li>
 *   <li>状态信息（状态、错误信息）</li>
 *   <li>盈亏信息（盈亏金额、手续费）</li>
 *   <li>时间信息（信号时间、下单时间、成交时间）</li>
 * </ul>
 * </p>
 *
 * <h3>订单状态流转：</h3>
 * <pre>
 * PENDING (待成交) → SUCCESS (已成交) 或 FAILED (失败)
 * </pre>
 *
 * <h3>字段命名说明：</h3>
 * <ul>
 *   <li>signalTime：策略产生信号的原始时间</li>
 *   <li>orderTime：实际调用 API 下单的时间</li>
 *   <li>fillTime：订单成交的时间</li>
 * </ul>
 *
 * @author Quant Trader
 * @version 1.0.0
 */
public class TradingRecord {

    // ==================== 状态常量 ====================

    /** 状态：订单待处理（刚创建订单，未确认成交） */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态：订单已成交 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 状态：订单失败 */
    public static final String STATUS_FAILED = "FAILED";

    // ==================== 方向常量 ====================

    /** 方向：开多仓 */
    public static final String DIR_OPEN_LONG = "OPEN_LONG";

    /** 方向：平多仓 */
    public static final String DIR_CLOSE_LONG = "CLOSE_LONG";

    /** 方向：开空仓 */
    public static final String DIR_OPEN_SHORT = "OPEN_SHORT";

    /** 方向：平空仓 */
    public static final String DIR_CLOSE_SHORT = "CLOSE_SHORT";

    // ==================== 字段 ====================

    /** 数据库自增主键 */
    private Long id;

    /** 订单ID（本地生成，用于关联订单状态更新） */
    private String orderId;

    /** 合约名称（如 BTC_USDT） */
    private String contract;

    /** 信号类型（BUY=买入开仓 / SELL=卖出平仓 / STOP_LOSS=止损 / TAKE_PROFIT=止盈） */
    private String signalType;

    /** 信号原因（详细描述触发原因） */
    private String signalReason;

    /** 交易方向 */
    private String direction;

    /** 成交张数 */
    private BigDecimal size;

    /** 成交价格 */
    private BigDecimal price;

    /** 订单类型（MARKET=市价单 / LIMIT=限价单） */
    private String orderType;

    /** 订单状态 */
    private String status;

    /** 错误信息（订单失败时填写） */
    private String errorMessage;

    /** 平仓盈亏（平仓时填写） */
    private BigDecimal pnl;

    /** 手续费 */
    private BigDecimal commission;

    /** 信号产生时间 */
    private LocalDateTime signalTime;

    /** 实际下单时间 */
    private LocalDateTime orderTime;

    /** 订单成交时间 */
    private LocalDateTime fillTime;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    // ==================== 构造方法 ====================

    /**
     * 默认构造函数
     */
    public TradingRecord() {
        this.status = STATUS_PENDING;
        this.createdAt = LocalDateTime.now();
        this.signalTime = LocalDateTime.now();
    }

    /**
     * 构造函数
     *
     * @param orderId    订单ID
     * @param contract  合约名称
     * @param signalType 信号类型
     * @param direction 交易方向
     */
    public TradingRecord(String orderId, String contract, String signalType, String direction) {
        this();
        this.orderId = orderId;
        this.contract = contract;
        this.signalType = signalType;
        this.direction = direction;
    }

    // ==================== Getter / Setter ====================

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

    // ==================== 辅助方法 ====================

    /**
     * 判断订单是否已成功
     *
     * @return true = 已成交
     */
    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    /**
     * 判断订单是否失败
     *
     * @return true = 失败
     */
    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }

    /**
     * 判断是否为开仓订单
     *
     * @return true = 开仓
     */
    public boolean isOpening() {
        return DIR_OPEN_LONG.equals(direction) || DIR_OPEN_SHORT.equals(direction);
    }

    /**
     * 判断是否为平仓订单
     *
     * @return true = 平仓
     */
    public boolean isClosing() {
        return DIR_CLOSE_LONG.equals(direction) || DIR_CLOSE_SHORT.equals(direction);
    }

    /**
     * 判断是否有多仓
     *
     * @return true = 多仓
     */
    public boolean isLong() {
        return DIR_OPEN_LONG.equals(direction) || DIR_CLOSE_LONG.equals(direction);
    }

    /**
     * 获取格式化的时间信息
     *
     * @return 格式：yyyy-MM-dd HH:mm:ss
     */
    public String getFormattedTime() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return signalTime != null ? signalTime.format(fmt) : "N/A";
    }

    @Override
    public String toString() {
        return String.format(
                "TradingRecord{orderId='%s', contract='%s', direction='%s', size=%s, price=%s, status='%s'}",
                orderId, contract, direction, size, price, status);
    }
}
