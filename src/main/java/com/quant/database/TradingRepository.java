package com.quant.database;

import com.quant.model.TradingRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 实盘交易记录数据访问层
 */
public class TradingRepository {

    private static final Logger log = LoggerFactory.getLogger(TradingRepository.class);

    private final DatabaseManager db;

    public TradingRepository() {
        this.db = DatabaseManager.getInstance();
    }

    /**
     * 保存交易记录
     * 
     * 所有交易记录都会持久化到 MySQL 数据库。
     * 
     * @param record 交易记录对象
     */
    public void save(TradingRecord record) {

        String sql = "INSERT INTO trading_records (" +
                "order_id, contract, signal_type, signal_reason, direction, " +
                "size, price, order_type, status, error_message, pnl, commission, " +
                "signal_time, order_time, fill_time, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "status = VALUES(status), " +
                "error_message = VALUES(error_message), " +
                "price = VALUES(price), " +
                "fill_time = VALUES(fill_time), " +
                "pnl = VALUES(pnl), " +
                "commission = VALUES(commission)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, record.getOrderId());
            stmt.setString(2, record.getContract());
            stmt.setString(3, record.getSignalType());
            stmt.setString(4, record.getSignalReason());
            stmt.setString(5, record.getDirection());
            stmt.setBigDecimal(6, record.getSize());
            stmt.setBigDecimal(7, record.getPrice());
            stmt.setString(8, record.getOrderType());
            stmt.setString(9, record.getStatus());
            stmt.setString(10, record.getErrorMessage());
            stmt.setBigDecimal(11, record.getPnl());
            stmt.setBigDecimal(12, record.getCommission());
            stmt.setTimestamp(13, Timestamp.valueOf(record.getSignalTime()));
            stmt.setTimestamp(14, record.getOrderTime() != null ? Timestamp.valueOf(record.getOrderTime()) : null);
            stmt.setTimestamp(15, record.getFillTime() != null ? Timestamp.valueOf(record.getFillTime()) : null);
            stmt.setTimestamp(16, Timestamp.valueOf(record.getCreatedAt()));

            stmt.executeUpdate();
            log.debug("交易记录已保存: {}", record.getOrderId());

        } catch (SQLException e) {
            log.error("保存交易记录失败: {}", record.getOrderId(), e);
        }
    }

    /**
     * 更新交易记录状态
     * 
     * @param orderId 订单ID
     * @param status 新状态
     * @param errorMessage 错误信息（可为空）
     */
    public void updateStatus(String orderId, String status, String errorMessage) {

        String sql = "UPDATE trading_records SET status = ?, error_message = ? WHERE order_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setString(2, errorMessage);
            stmt.setString(3, orderId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            log.error("更新交易状态失败: {}", orderId, e);
        }
    }

    

    

    

    // ==================== 私有方法 ====================

    private TradingRecord mapResultSetToRecord(ResultSet rs) throws SQLException {
        TradingRecord record = new TradingRecord();
        record.setId(rs.getLong("id"));
        record.setOrderId(rs.getString("order_id"));
        record.setContract(rs.getString("contract"));
        record.setSignalType(rs.getString("signal_type"));
        record.setSignalReason(rs.getString("signal_reason"));
        record.setDirection(rs.getString("direction"));
        record.setSize(rs.getBigDecimal("size"));
        record.setPrice(rs.getBigDecimal("price"));
        record.setOrderType(rs.getString("order_type"));
        record.setStatus(rs.getString("status"));
        record.setErrorMessage(rs.getString("error_message"));
        record.setPnl(toBigDecimal(rs.getBigDecimal("pnl")));
        record.setCommission(toBigDecimal(rs.getBigDecimal("commission")));
        record.setSignalTime(toLocalDateTime(rs.getTimestamp("signal_time")));
        Timestamp orderTime = rs.getTimestamp("order_time");
        if (orderTime != null) {
            record.setOrderTime(orderTime.toLocalDateTime());
        }
        Timestamp fillTime = rs.getTimestamp("fill_time");
        if (fillTime != null) {
            record.setFillTime(fillTime.toLocalDateTime());
        }
        record.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        return record;
    }

    /** BigDecimal null 安全，null → ZERO */
    private BigDecimal toBigDecimal(BigDecimal bd) {
        return bd != null ? bd : BigDecimal.ZERO;
    }

    /** Timestamp → LocalDateTime，null 安全 */
    private LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    
}
