package com.quant.database;

import com.quant.backtest.BacktestTrade;
import com.quant.model.BacktestReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 回测数据访问层（DAO）
 * <p>
 * 负责将回测报告和回测交易记录持久化到 MySQL 数据库。
 * 支持回测报告的保存/更新和回测交易记录的批量保存。
 * </p>
 *
 * <h3>数据表结构：</h3>
 * <pre>
 * backtest_reports (
 *   id, report_id, contract, strategy_name, start_time, end_time,
 *   kline_interval, initial_capital, commission_rate, final_capital,
 *   total_return, total_trades, win_trades, loss_trades, win_rate,
 *   max_drawdown, sharpe_ratio, created_at
 * )
 *
 * backtest_trades (
 *   trade_id, report_id, contract, signal_type, entry_time, entry_price,
 *   exit_time, exit_price, size, direction, pnl, commission, return_pct,
 *   holding_period, max_profit, max_loss, created_at
 * )
 * </pre>
 *
 * @author Quant Trader
 * @version 1.0.0
 * @see BacktestReport
 * @see BacktestTrade
 */
public class BacktestRepository {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(BacktestRepository.class);

    /** 数据库管理器单例 */
    private final DatabaseManager db;

    /**
     * 构造函数 - 初始化数据库管理器
     */
    public BacktestRepository() {
        this.db = DatabaseManager.getInstance();
    }

    /**
     * 保存或更新回测报告
     * <p>
     * 使用 INSERT ... ON DUPLICATE KEY UPDATE 语法：
     * <ul>
     *   <li>如果 report_id 不存在，执行 INSERT</li>
     *   <li>如果 report_id 已存在，执行 UPDATE（更新回测结果数据）</li>
     * </ul>
     * </p>
     *
     * @param report 回测报告对象，不能为 null
     */
    public void saveReport(BacktestReport report) {
        if (report == null) {
            log.warn("试图保存空回测报告，已忽略");
            return;
        }

        final String SQL =
            "INSERT INTO backtest_reports (" +
            "    report_id, contract, strategy_name, start_time, end_time, " +
            "    kline_interval, initial_capital, commission_rate, final_capital, " +
            "    total_return, total_trades, win_trades, loss_trades, win_rate, " +
            "    max_drawdown, sharpe_ratio, created_at" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" +
            " ON DUPLICATE KEY UPDATE" +
            "    final_capital = VALUES(final_capital)," +
            "    total_return = VALUES(total_return)," +
            "    total_trades = VALUES(total_trades)," +
            "    win_trades = VALUES(win_trades)," +
            "    loss_trades = VALUES(loss_trades)," +
            "    win_rate = VALUES(win_rate)," +
            "    max_drawdown = VALUES(max_drawdown)," +
            "    sharpe_ratio = VALUES(sharpe_ratio)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {

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
            stmt.setTimestamp(17, Timestamp.valueOf(
                    report.getCreatedAt() != null ? report.getCreatedAt() : LocalDateTime.now()));

            stmt.executeUpdate();
            log.info("回测报告已保存: reportId={}, contract={}, totalTrades={}",
                    report.getReportId(), report.getContract(), report.getTotalTrades());

        } catch (SQLException e) {
            log.error("保存回测报告失败: reportId={}", report.getReportId(), e);
        }
    }

    /**
     * 批量保存回测交易记录
     * <p>
     * 使用 JDBC 批量写入（addBatch + executeBatch）提高性能。
     * 整个批量操作在同一事务中执行，确保数据一致性。
     * </p>
     *
     * @param reportId 回测报告ID（关联外键）
     * @param contract 合约名称
     * @param trades  交易记录列表（可为空列表）
     */
    public void saveTrades(String reportId, String contract, java.util.List<BacktestTrade> trades) {
        if (trades == null || trades.isEmpty()) {
            log.debug("交易列表为空，跳过保存: reportId={}", reportId);
            return;
        }

        final String SQL =
            "INSERT INTO backtest_trades (" +
            "    trade_id, report_id, contract, signal_type, entry_time, entry_price, " +
            "    exit_time, exit_price, size, direction, pnl, commission, return_pct, " +
            "    holding_period, max_profit, max_loss, created_at" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {

            // 关闭自动提交，启用手动事务
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
                stmt.setInt(14, 0);  // holding_period（暂未实现）
                stmt.setBigDecimal(15, BigDecimal.valueOf(trade.getPnlGross()));
                stmt.setBigDecimal(16, BigDecimal.ZERO);  // max_loss（暂未实现）
                stmt.setTimestamp(17, Timestamp.valueOf(LocalDateTime.now()));
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();

            log.info("回测交易记录已批量保存: count={}, reportId={}", trades.size(), reportId);

        } catch (SQLException e) {
            log.error("批量保存回测交易记录失败: reportId={}", reportId, e);
        }
    }
}
