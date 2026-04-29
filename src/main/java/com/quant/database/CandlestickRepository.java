package com.quant.database;

import com.quant.model.Candlestick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * K线数据访问层（DAO）
 * <p>
 * 负责将K线数据（蜡烛图）持久化到 MySQL 数据库。
 * 支持回测K线和实盘K线缓存的保存、查询和清理。
 * </p>
 *
 * <h3>数据表结构：</h3>
 * <pre>
 * backtest_candles (
 *   id, report_id, contract, kline_period, bar_time,
 *   open_price, high_price, low_price, close_price, volume, quote_volume, created_at
 * )
 *
 * realtime_candles (
 *   id, contract, kline_period, symbol_name, bar_time,
 *   open_price, high_price, low_price, close_price, volume, quote_volume,
 *   created_at, updated_at
 * )
 * </pre>
 *
 * @author Quant Trader
 * @version 1.0.0
 * @see Candlestick
 */
public class CandlestickRepository {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(CandlestickRepository.class);

    /** 数据库管理器单例 */
    private final DatabaseManager db;

    /** 批量保存的阈值（每N条执行一次批量提交） */
    private static final int BATCH_THRESHOLD = 1000;

    /**
     * 构造函数 - 初始化数据库管理器
     */
    public CandlestickRepository() {
        this.db = DatabaseManager.getInstance();
    }

    // ==================== 写入操作 ====================

    /**
     * 批量保存回测K线数据
     * <p>
     * 使用 JDBC 批量写入提高性能，每1000条执行一次批量提交以避免内存溢出。
     * </p>
     *
     * @param reportId 关联的回测报告ID
     * @param contract 合约名称
     * @param interval K线周期（如 15m, 1h）
     * @param candles  K线数据列表（可为空列表）
     */
    public void saveBacktestCandles(String reportId, String contract, String interval,
                                    List<Candlestick> candles) {
        if (candles == null || candles.isEmpty()) {
            log.debug("K线列表为空，跳过保存: reportId={}", reportId);
            return;
        }

        final String SQL =
            "INSERT INTO backtest_candles (" +
            "    report_id, contract, kline_period, bar_time, " +
            "    open_price, high_price, low_price, close_price, volume, quote_volume, " +
            "    created_at" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {

            conn.setAutoCommit(false);

            int batchCount = 0;
            for (Candlestick candle : candles) {
                stmt.setString(1, reportId);
                stmt.setString(2, contract);
                stmt.setString(3, interval);
                stmt.setTimestamp(4, Timestamp.from(Instant.ofEpochSecond(candle.getTimestamp())));
                stmt.setBigDecimal(5, BigDecimal.valueOf(candle.getOpen()));
                stmt.setBigDecimal(6, BigDecimal.valueOf(candle.getHigh()));
                stmt.setBigDecimal(7, BigDecimal.valueOf(candle.getLow()));
                stmt.setBigDecimal(8, BigDecimal.valueOf(candle.getClose()));
                stmt.setBigDecimal(9, BigDecimal.valueOf(candle.getVolume()));
                stmt.setBigDecimal(10, BigDecimal.valueOf(candle.getQuoteVolume()));
                stmt.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
                stmt.addBatch();

                batchCount++;
                // 每1000条执行一次批量提交，避免内存溢出
                if (batchCount % BATCH_THRESHOLD == 0) {
                    stmt.executeBatch();
                    log.debug("K线数据批量保存进度: {}/{}", batchCount, candles.size());
                }
            }

            stmt.executeBatch();
            conn.commit();
            log.info("回测K线数据已批量保存: total={}, reportId={}", candles.size(), reportId);

        } catch (SQLException e) {
            log.error("批量保存回测K线数据失败: reportId={}", reportId, e);
        }
    }

    /**
     * 保存单条实盘K线数据（用于实时缓存）
     * <p>
     * 使用 INSERT ... ON DUPLICATE KEY UPDATE 语法，
     * 如果同一时间周期的K线已存在则更新，否则插入新记录。
     * </p>
     *
     * @param candle   K线数据对象
     * @param contract 合约名称
     * @param interval K线周期（如 15m, 1h）
     */
    public void saveRealtimeCandle(Candlestick candle, String contract, String interval) {
        if (candle == null) {
            return;
        }

        final String SQL =
            "INSERT INTO realtime_candles (" +
            "    contract, kline_period, symbol_name, bar_time, " +
            "    open_price, high_price, low_price, close_price, volume, quote_volume, " +
            "    created_at, updated_at" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" +
            " ON DUPLICATE KEY UPDATE" +
            "    open_price = VALUES(open_price)," +
            "    high_price = VALUES(high_price)," +
            "    low_price = VALUES(low_price)," +
            "    close_price = VALUES(close_price)," +
            "    volume = VALUES(volume)," +
            "    quote_volume = VALUES(quote_volume)," +
            "    updated_at = CURRENT_TIMESTAMP";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {

            stmt.setString(1, contract);
            stmt.setString(2, interval);
            stmt.setString(3, contract);  // symbol 同 contract
            stmt.setTimestamp(4, Timestamp.from(Instant.ofEpochSecond(candle.getTimestamp())));
            stmt.setBigDecimal(5, BigDecimal.valueOf(candle.getOpen()));
            stmt.setBigDecimal(6, BigDecimal.valueOf(candle.getHigh()));
            stmt.setBigDecimal(7, BigDecimal.valueOf(candle.getLow()));
            stmt.setBigDecimal(8, BigDecimal.valueOf(candle.getClose()));
            stmt.setBigDecimal(9, BigDecimal.valueOf(candle.getVolume()));
            stmt.setBigDecimal(10, BigDecimal.valueOf(candle.getQuoteVolume()));
            stmt.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();

        } catch (SQLException e) {
            log.error("保存实盘K线数据失败: contract={}, interval={}", contract, interval, e);
        }
    }

    // ==================== 查询操作 ====================

    /**
     * 查询指定回测报告的K线数据
     * <p>
     * 按 bar_time 升序返回，便于在回测时按时间顺序处理。
     * </p>
     *
     * @param reportId 回测报告ID
     * @return K线数据列表（按时间正序），查询失败返回空列表
     */
    public List<Candlestick> findBacktestCandles(String reportId) {
        List<Candlestick> candles = new ArrayList<>();

        final String SQL = "SELECT * FROM backtest_candles WHERE report_id = ? ORDER BY bar_time ASC";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {

            stmt.setString(1, reportId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                candles.add(mapResultSetToCandlestick(rs));
            }

            log.debug("查询回测K线数据: reportId={}, count={}", reportId, candles.size());

        } catch (SQLException e) {
            log.error("查询回测K线数据失败: reportId={}", reportId, e);
        }

        return candles;
    }

    /**
     * 查询指定合约的实盘K线缓存
     * <p>
     * 按 bar_time 降序查询后反转回正序，返回最近 limit 条记录。
     * </p>
     *
     * @param contract 合约名称
     * @param interval K线周期
     * @param limit    返回记录数量上限
     * @return K线数据列表（按时间正序），查询失败返回空列表
     */
    public List<Candlestick> findRealtimeCandles(String contract, String interval, int limit) {
        List<Candlestick> candles = new ArrayList<>();

        final String SQL =
            "SELECT * FROM realtime_candles " +
            "WHERE contract = ? AND kline_period = ? " +
            "ORDER BY bar_time DESC LIMIT ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {

            stmt.setString(1, contract);
            stmt.setString(2, interval);
            stmt.setInt(3, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                candles.add(mapResultSetToCandlestick(rs));
            }

            // 反转为正序
            java.util.Collections.reverse(candles);

        } catch (SQLException e) {
            log.error("查询实盘K线缓存失败: contract={}, interval={}", contract, interval, e);
        }

        return candles;
    }

    // ==================== 删除操作 ====================

    /**
     * 删除指定报告的K线数据
     * <p>
     * 用于清理回测完成后不再需要的临时K线数据。
     * </p>
     *
     * @param reportId 回测报告ID
     */
    public void deleteBacktestCandles(String reportId) {
        final String SQL = "DELETE FROM backtest_candles WHERE report_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {

            stmt.setString(1, reportId);
            int deleted = stmt.executeUpdate();
            log.info("删除回测K线数据: reportId={}, count={}", reportId, deleted);

        } catch (SQLException e) {
            log.error("删除回测K线数据失败: reportId={}", reportId, e);
        }
    }

    /**
     * 清理过期的实盘K线缓存
     * <p>
     * 删除早于 retainDays 天的实盘K线缓存数据。
     * 建议定期调用（如每天一次），以免数据库积累过多历史数据。
     * </p>
     *
     * @param retainDays 保留天数（超过此天数的数据将被删除）
     */
    public void cleanupRealtimeCandles(int retainDays) {
        final String SQL = "DELETE FROM realtime_candles WHERE bar_time < DATE_SUB(NOW(), INTERVAL ? DAY)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {

            stmt.setInt(1, retainDays);
            int deleted = stmt.executeUpdate();
            log.info("清理过期实盘K线缓存: retainDays={}, deleted={}", retainDays, deleted);

        } catch (SQLException e) {
            log.error("清理实盘K线缓存失败", e);
        }
    }

    // ==================== 私有工具方法 ====================

    /**
     * 将 ResultSet 映射为 Candlestick 对象
     *
     * @param rs 数据库结果集（当前行必须有效）
     * @return Candlestick 对象
     * @throws SQLException 如果读取字段失败
     */
    private Candlestick mapResultSetToCandlestick(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("bar_time");
        long epochSec = ts != null ? ts.toInstant().getEpochSecond() : 0L;

        return Candlestick.builder()
                .timestamp(epochSec)
                .open(toDouble(rs.getBigDecimal("open_price")))
                .high(toDouble(rs.getBigDecimal("high_price")))
                .low(toDouble(rs.getBigDecimal("low_price")))
                .close(toDouble(rs.getBigDecimal("close_price")))
                .volume(toDouble(rs.getBigDecimal("volume")))
                .quoteVolume(toDouble(rs.getBigDecimal("quote_volume")))
                .build();
    }

    /**
     * BigDecimal 安全转换为 double
     * <p>
     * 如果 BigDecimal 为 null，则返回 0.0。
     * </p>
     *
     * @param bd 原始 BigDecimal（可为 null）
     * @return double 值
     */
    private double toDouble(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0.0;
    }
}
