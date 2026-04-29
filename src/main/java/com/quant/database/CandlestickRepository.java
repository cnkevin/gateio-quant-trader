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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * K线数据访问层
 */
public class CandlestickRepository {

    private static final Logger log = LoggerFactory.getLogger(CandlestickRepository.class);

    private final DatabaseManager db;

    public CandlestickRepository() {
        this.db = DatabaseManager.getInstance();
    }

    /**
     * 批量保存回测K线数据
     * 
     * 所有K线数据都会持久化到 MySQL 数据库。
     * 如果蜡烛图数据为空，则直接返回。
     *
     * @param reportId 关联的回测报告ID
     * @param contract 合约名称
     * @param interval K线周期
     * @param candles  K线数据列表
     */
    public void saveBacktestCandles(String reportId, String contract, String interval,
                                    List<Candlestick> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO backtest_candles (" +
                "report_id, contract, kline_period, bar_time, " +
                "open_price, high_price, low_price, close_price, volume, quote_volume, " +
                "created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

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
                if (batchCount % 1000 == 0) {
                    stmt.executeBatch();
                    log.debug("K线数据批量保存进度: {}/{}", batchCount, candles.size());
                }
            }

            stmt.executeBatch();
            conn.commit();
            log.info("回测K线数据已批量保存: {} 条 (report_id={})", candles.size(), reportId);

        } catch (SQLException e) {
            log.error("保存回测K线数据失败: report_id={}", reportId, e);
        }
    }

    /**
     * 保存单条K线数据（用于实盘缓存）
     * 
     * @param candle K线数据对象
     * @param contract 合约名称
     * @param interval K线周期
     */
    public void saveRealtimeCandle(Candlestick candle, String contract, String interval) {
        if (candle == null) {
            return;
        }

        String sql = "INSERT INTO realtime_candles (" +
                "contract, kline_period, symbol_name, bar_time, " +
                "open_price, high_price, low_price, close_price, volume, quote_volume, " +
                "created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "open_price = VALUES(open_price), " +
                "high_price = VALUES(high_price), " +
                "low_price = VALUES(low_price), " +
                "close_price = VALUES(close_price), " +
                "volume = VALUES(volume), " +
                "quote_volume = VALUES(quote_volume), " +
                "updated_at = CURRENT_TIMESTAMP";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

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
            log.error("保存实盘K线数据失败: contract={}", contract, e);
        }
    }

/**
     * 查询指定回测报告的K线数据
     * 
     * 所有K线数据都会从 MySQL 数据库中查询。
     * 如果查询过程中发生异常，会记录错误日志并返回空列表。
     * 
     * @param reportId 回测报告ID
     * @return K线数据列表（查询失败时返回空列表）
     */
    public List<Candlestick> findBacktestCandles(String reportId) {
        List<Candlestick> candles = new ArrayList<>();

        String sql = "SELECT * FROM backtest_candles WHERE report_id = ? ORDER BY bar_time ASC";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reportId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                candles.add(mapResultSetToCandlestick(rs));
            }

            log.debug("查询回测K线数据: report_id={}, count={}", reportId, candles.size());

        } catch (SQLException e) {
            log.error("查询回测K线数据失败: report_id={}", reportId, e);
        }

        return candles;
    }

    /**
     * 查询指定合约的实盘K线缓存
     * 
     * 用于查询缓存的实盘K线数据。
     * 如果查询过程中发生异常，会记录错误日志并返回空列表。
     * 
     * @param contract 合约名称
     * @param interval K线周期
     * @param limit 返回记录数量上限
     * @return K线数据列表（查询失败时返回空列表）
     */
    public List<Candlestick> findRealtimeCandles(String contract, String interval, int limit) {
        List<Candlestick> candles = new ArrayList<>();

        String sql = "SELECT * FROM realtime_candles WHERE contract = ? AND kline_period = ? ORDER BY bar_time DESC LIMIT ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

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
            log.error("查询实盘K线缓存失败: contract={}", contract, e);
        }

        return candles;
    }

    /**
     * 删除指定报告的K线数据
     * 
     * 用于清理回测过程中产生的临时K线数据。
     * 如果删除过程中发生异常，会记录错误日志，但不会抛出异常影响主流程。
     * 
     * @param reportId 回测报告ID
     */
    public void deleteBacktestCandles(String reportId) {

        String sql = "DELETE FROM backtest_candles WHERE report_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int deleted = stmt.executeUpdate();
            log.info("删除回测K线数据: report_id={}, count={}", reportId, deleted);

        } catch (SQLException e) {
            log.error("删除回测K线数据失败: report_id={}", reportId, e);
        }
    }

    /**
     * 清理过期的实盘K线缓存（保留最近 N 天）
     * 
     * 用于清理过期的实盘K线缓存数据。
     * 建议定期调用此方法（如每天一次），以免数据库积累过多历史数据。
     * 
     * @param retainDays 保留天数（超过此天数的数据将被删除）
     */
    public void cleanupRealtimeCandles(int retainDays) {

        String sql = "DELETE FROM realtime_candles WHERE bar_time < DATE_SUB(NOW(), INTERVAL ? DAY)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, retainDays);
            int deleted = stmt.executeUpdate();
            log.info("清理过期实盘K线缓存: retain_days={}, deleted={}", retainDays, deleted);

        } catch (SQLException e) {
            log.error("清理实盘K线缓存失败", e);
        }
    }

    // ==================== 私有方法 ====================

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

    /** BigDecimal → double，null 安全 */
    private double toDouble(java.math.BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0.0;
    }
}
