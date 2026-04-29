package com.quant.database;

import com.quant.backtest.BacktestTrade;
import com.quant.model.BacktestReport;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 回测数据访问层
 */
public class BacktestRepository {

    private static final Logger log = LoggerFactory.getLogger(BacktestRepository.class);

    private final DatabaseManager db;

    public BacktestRepository() {
        this.db = DatabaseManager.getInstance();
    }

    /**
     * 保存回测报告
     * 
     * 所有回测报告都会持久化到 MySQL 数据库。
     * 报告ID唯一，重复保存会自动更新现有记录。
     * 
     * @param report 回测报告对象
     */
    public void saveReport(BacktestReport report) {

        String sql = "INSERT INTO backtest_reports (" +
                "report_id, contract, strategy_name, start_time, end_time, " +
                "kline_interval, initial_capital, commission_rate, final_capital, " +
                "total_return, total_trades, win_trades, loss_trades, win_rate, " +
                "max_drawdown, sharpe_ratio, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "final_capital = VALUES(final_capital), " +
                "total_return = VALUES(total_return), " +
                "total_trades = VALUES(total_trades), " +
                "win_trades = VALUES(win_trades), " +
                "loss_trades = VALUES(loss_trades), " +
                "win_rate = VALUES(win_rate), " +
                "max_drawdown = VALUES(max_drawdown), " +
                "sharpe_ratio = VALUES(sharpe_ratio)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, report.getReportId());
            stmt.setString(2, report.getContract());
            stmt.setString(3, report.getStrategyName());
            stmt.setTimestamp(4, Timestamp.valueOf(report.getStartTime()));
            stmt.setTimestamp(5, Timestamp.valueOf(report.getEndTime()));
            stmt.setString(6, report.getKlineInterval());
            stmt.setBigDecimal(7, report.getInitialCapital());
            stmt.setBigDecimal(8, report.getCommissionRate());
            stmt.setBigDecimal(9, report.getFinalCapital());
            stmt.setBigDecimal(10, report.getTotalReturn());
            stmt.setInt(11, report.getTotalTrades());
            stmt.setInt(12, report.getWinTrades());
            stmt.setInt(13, report.getLossTrades());
            stmt.setBigDecimal(14, report.getWinRate());
            stmt.setBigDecimal(15, report.getMaxDrawdown());
            stmt.setBigDecimal(16, report.getSharpeRatio());
            stmt.setTimestamp(17, Timestamp.valueOf(report.getCreatedAt() != null ? report.getCreatedAt() : LocalDateTime.now()));

            stmt.executeUpdate();
            log.info("回测报告已保存: {}", report.getReportId());

        } catch (SQLException e) {
            log.error("保存回测报告失败: {}", report.getReportId(), e);
        }
    }

    /**
     * 批量保存回测交易记录（从 BacktestRunner 的 domain model）
     * 
     * 所有回测交易记录都会持久化到 MySQL 数据库。
     * 如果交易列表为空，则直接返回；批量插入使用事务确保数据一致性。
     * 
     * @param reportId 回测报告ID
     * @param contract 合约名称
     * @param trades 交易记录列表（可为空）
     */
    public void saveTrades(String reportId, String contract, List<BacktestTrade> trades) {
        if (trades == null || trades.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO backtest_trades (" +
                "trade_id, report_id, contract, signal_type, entry_time, entry_price, " +
                "exit_time, exit_price, size, direction, pnl, commission, return_pct, " +
                "holding_period, max_profit, max_loss, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (BacktestTrade trade : trades) {
                String tradeId = java.util.UUID.randomUUID().toString();
                stmt.setString(1, tradeId);
                stmt.setString(2, reportId);
                stmt.setString(3, contract);
                stmt.setString(4, trade.getExitType());
                stmt.setTimestamp(5, Timestamp.from(Instant.ofEpochSecond(trade.getEntryTime())));
                stmt.setBigDecimal(6, BigDecimal.valueOf(trade.getEntryPrice()));
                stmt.setTimestamp(7, Timestamp.from(Instant.ofEpochSecond(trade.getExitTime())));
                stmt.setBigDecimal(8, BigDecimal.valueOf(trade.getExitPrice()));
                stmt.setBigDecimal(9, BigDecimal.valueOf(trade.getSize()));
                stmt.setString(10, "LONG");  // 默认做多
                stmt.setBigDecimal(11, BigDecimal.valueOf(trade.getPnlNet()));
                stmt.setBigDecimal(12, BigDecimal.valueOf(trade.getCommission()));
                stmt.setBigDecimal(13, BigDecimal.valueOf(trade.getReturnPct()));
                stmt.setInt(14, 0);  // holding_period
                stmt.setBigDecimal(15, BigDecimal.valueOf(trade.getPnlGross()));
                stmt.setBigDecimal(16, BigDecimal.ZERO);
                stmt.setTimestamp(17, Timestamp.valueOf(LocalDateTime.now()));
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();
            log.info("回测交易记录已批量保存: {} 条", trades.size());

        } catch (SQLException e) {
            log.error("保存回测交易记录失败", e);
        }
    }

    

    

    // ==================== 私有方法 ====================

    private BacktestReport mapResultSetToReport(ResultSet rs) throws SQLException {
        BacktestReport report = new BacktestReport();
        report.setId(rs.getLong("id"));
        report.setReportId(rs.getString("report_id"));
        report.setContract(rs.getString("contract"));
        report.setStrategyName(rs.getString("strategy_name"));
        report.setStartTime(toLocalDateTime(rs.getTimestamp("start_time")));
        report.setEndTime(toLocalDateTime(rs.getTimestamp("end_time")));
        report.setKlineInterval(rs.getString("kline_interval"));
        report.setInitialCapital(rs.getBigDecimal("initial_capital"));
        report.setCommissionRate(rs.getBigDecimal("commission_rate"));
        report.setFinalCapital(rs.getBigDecimal("final_capital"));
        report.setTotalReturn(rs.getBigDecimal("total_return"));
        report.setTotalTrades(rs.getInt("total_trades"));
        report.setWinTrades(rs.getInt("win_trades"));
        report.setLossTrades(rs.getInt("loss_trades"));
        report.setWinRate(rs.getBigDecimal("win_rate"));
        report.setMaxDrawdown(rs.getBigDecimal("max_drawdown"));
        report.setSharpeRatio(rs.getBigDecimal("sharpe_ratio"));
        report.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        return report;
    }

    private LocalDateTime toLocalDateTime(java.sql.Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
