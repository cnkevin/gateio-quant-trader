package com.quant.database;

import com.quant.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库连接管理器
 * 使用 HikariCP 连接池
 */
public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private static DatabaseManager instance;
    private final DataSource dataSource;
    private final boolean enabled;

    private DatabaseManager() {
        AppConfig.DatabaseConfig dbConfig = AppConfig.getInstance().getDatabase();

        // 检查是否启用数据库
        this.enabled = dbConfig != null && dbConfig.isEnabled();

        if (!enabled) {
            log.info("数据库未启用，跳过连接初始化");
            this.dataSource = null;
            return;
        }

        // 构建 JDBC URL
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                dbConfig.getHost(), dbConfig.getPort(), dbConfig.getName());

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbConfig.getUsername());
        config.setPassword(dbConfig.getPassword());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // 连接池配置
        AppConfig.DatabaseConfig.PoolConfig poolConfig = dbConfig.getPool();
        if (poolConfig != null) {
            config.setMinimumIdle(poolConfig.getMinimumIdle());
            config.setMaximumPoolSize(poolConfig.getMaximumPoolSize());
            config.setMaxLifetime(poolConfig.getMaxLifetime());
            config.setConnectionTimeout(poolConfig.getConnectionTimeout());
            config.setIdleTimeout(poolConfig.getIdleTimeout());
        } else {
            // 默认配置
            config.setMinimumIdle(5);
            config.setMaximumPoolSize(20);
        }

        // 连接池名称
        config.setPoolName("QuantTrader-HikariCP");

        // 连接测试
        config.setConnectionTestQuery("SELECT 1");

        this.dataSource = new HikariDataSource(config);
        log.info("数据库连接池初始化完成: {}:{}/{}", dbConfig.getHost(), dbConfig.getPort(), dbConfig.getName());
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() throws SQLException {
        if (!enabled || dataSource == null) {
            throw new SQLException("数据库未启用");
        }
        return dataSource.getConnection();
    }

    /**
     * 获取数据源
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * 检查数据库是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 关闭连接池
     */
    public void shutdown() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
            log.info("数据库连接池已关闭");
        }
    }

    /**
     * 测试数据库连接
     */
    public boolean testConnection() {
        if (!enabled) {
            return false;
        }
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.error("数据库连接测试失败", e);
            return false;
        }
    }

    /**
     * 初始化数据库表结构
     */
    public void initializeSchema() {
        if (!enabled) {
            log.warn("数据库未启用，跳过表结构初始化");
            return;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // 创建数据库（如果不存在）
            String createDbSql = String.format(
                    "CREATE DATABASE IF NOT EXISTS %s CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
                    AppConfig.getInstance().getDatabase().getName());
            stmt.execute(createDbSql);

            // 执行建表 SQL 文件
            ClassLoader classLoader = getClass().getClassLoader();
            java.io.InputStream is = classLoader.getResourceAsStream("schema.sql");
            if (is != null) {
                String schemaSql = new String(is.readAllBytes());
                // 先去除 SQL 注释，再用 ";;" 双分号分割（schema.sql 中已规范使用）
                String cleaned = schemaSql.replaceAll("(?m)--.*$", "");
                for (String sql : cleaned.split(";")) {
                    sql = sql.trim();
                    if (!sql.isEmpty()) {
                        stmt.execute(sql);
                    }
                }
                log.info("数据库表结构初始化完成");
            }
        } catch (Exception e) {
            log.error("数据库表结构初始化失败", e);
        }
    }
}
