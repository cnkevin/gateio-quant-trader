package com.quant.database;

import com.quant.model.TradingRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * 实盘交易记录数据访问层（DAO）
 * <p>
 * 负责将交易记录（开仓/平仓）持久化到 MySQL 数据库的 trading_records 表。
 * 提供保存交易记录和更新交易状态的功能。
 * </p>
 *
 * <h3>数据表结构：</h3>
 * <pre>
 * trading_records (
 *   id, order_id, contract, signal_type, signal_reason, direction,
 *   size, price, order_type, status, error_message, pnl, commission,
 *   signal_time, order_time, fill_time, created_at
 * )
 * </pre>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * TradingRepository repo = new TradingRepository();
 * TradingRecord record = new TradingRecord(orderId, contract, "BUY", "OPEN_LONG");
 * repo.save(record);
 * }</pre>
 *
 * @author Quant Trader
 * @version 1.0.0
 * @see TradingRecord
 */
public class TradingRepository {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(TradingRepository.class);

    /** 数据库管理器单例 */
    private final DatabaseManager db;

    /**
     * 构造函数 - 初始化数据库管理器
     */
    public TradingRepository() {
        this.db = DatabaseManager.getInstance();
    }

    /**
     * 保存或更新交易记录
     * <p>
     * 使用 INSERT ... ON DUPLICATE KEY UPDATE 语法：
     * <ul>
     *   <li>如果 order_id 不存在，执行 INSERT</li>
     *   <li>如果 order_id 已存在，执行 UPDATE（更新状态、价格、成交时间等）</li>
     * </ul>
     * </p>
     *
     * @param record 交易记录对象，不能为 null
     */
    public void save(TradingRecord record) {
        if (record == null) {
            log.warn("试图保存空交易记录，已忽略");
            return;
        }

        final String SQL =
            "INSERT INTO trading_records (" +
            "    order_id, contract, signal_type, signal_reason, direction, " +
            "    size, price, order_type, status, error_message, pnl, commission, " +
            "    signal_time, order_time, fill_time, created_at" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" +
            " ON DUPLICATE KEY UPDATE" +
            "    status = VALUES(status)," +
            "    error_message = VALUES(error_message)," +
            "    price = VALUES(price)," +
            "    fill_time = VALUES(fill_time)," +
            "    pnl = VALUES(pnl)," +
            "    commission = VALUES(commission)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {

            // 设置参数
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
            log.debug("交易记录已保存: orderId={}, contract={}, type={}",
                    record.getOrderId(), record.getContract(), record.getSignalType());

        } catch (SQLException e) {
            log.error("保存交易记录失败: orderId={}", record.getOrderId(), e);
        }
    }

    /**
     * 更新交易记录状态
     * <p>
     * 通常在下单失败时调用，将 PENDING 状态更新为 FAILED。
     * </p>
     *
     * @param orderId      订单ID（主键）
     * @param status       新状态（如 FAILED）
     * @param errorMessage 错误信息（可为空）
     */
    public void updateStatus(String orderId, String status, String errorMessage) {
        if (orderId == null || orderId.isEmpty()) {
            log.warn("updateStatus: orderId 为空，已忽略");
            return;
        }

        final String SQL = "UPDATE trading_records SET status = ?, error_message = ? WHERE order_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {

            stmt.setString(1, status);
            stmt.setString(2, errorMessage);
            stmt.setString(3, orderId);
            stmt.executeUpdate();

            log.debug("交易状态已更新: orderId={}, status={}", orderId, status);

        } catch (SQLException e) {
            log.error("更新交易状态失败: orderId={}", orderId, e);
        }
    }
}
