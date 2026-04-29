package com.quant.database;

import com.quant.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库连接管理器
 * 使用 HikariCP 连接池
 */
public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private static DatabaseManager instance;
    private final DataSource dataSource;

    private DatabaseManager() {
        AppConfig.DatabaseConfig dbConfig = AppConfig.getInstance().getDatabase();
        
        if (dbConfig == null) {
            throw new IllegalStateException("数据库配置缺失，请检查 application.yml 配置文件");
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
     * 
     * 连接池在初始化时已确保可用。
     * 如果获取连接失败，将抛出 SQLException，调用方应适当处理。
     * 
     * @return 数据库连接
     * @throws SQLException 当数据库连接不可用时抛出
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
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
     * 
     * 连接测试失败意味着数据库不可用。
     * 建议在程序启动时调用此方法，确保数据库连接正常。
     * 
     * @return true 表示数据库连接正常，false 表示连接失败
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.error("数据库连接测试失败", e);
            return false;
        }
    }

    
}
