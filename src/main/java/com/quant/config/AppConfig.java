package com.quant.config;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 全局配置加载器（单例）
 * <p>
 * 负责从 classpath 中的 application.yml 读取所有配置项，
 * 并提供类型安全的访问接口。程序启动时调用 {@link #load()} 一次即可。
 * </p>
 *
 * <pre>
 * 配置文件优先级（从高到低）：
 *   1. JVM 系统属性 -Dconfig.file=&lt;外部路径&gt;
 *   2. classpath:/application.yml（默认）
 * </pre>
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    /** 单例实例 */
    private static volatile AppConfig instance;

    // ── 各配置节点 ──────────────────────────────────────────
    private ApiConfig api;
    private TradingConfig trading;
    private IndicatorConfig indicator;
    private BacktestConfig backtest;
    private LoggingConfig logging;
    private DatabaseConfig database;

    // ====================================================================
    //  静态工厂 / 单例获取
    // ====================================================================

    /** 私有构造，禁止外部实例化 */
    private AppConfig() {}

    /**
     * 获取全局配置单例。
     * 如果还未加载则先执行加载。
     *
     * @return 配置实例
     */
    public static AppConfig getInstance() {
        if (instance == null) {
            synchronized (AppConfig.class) {
                if (instance == null) {
                    instance = load();
                }
            }
        }
        return instance;
    }

    /**
     * 从 application.yml 加载配置，返回新的配置对象。
     * 外部调用者通常直接使用 {@link #getInstance()}，
     * 此方法保留用于测试或强制重新加载场景。
     *
     * @return 已填充数据的 AppConfig 实例
     */
    @SuppressWarnings("unchecked")
    public static AppConfig load() {
        Yaml yaml = new Yaml();
        String resourcePath = "application.yml";

        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("找不到配置文件: classpath:/" + resourcePath);
            }

            Map<String, Object> root = yaml.load(in);
            AppConfig cfg = new AppConfig();

            // ── 解析 api 节点 ──
            Map<String, Object> apiMap = (Map<String, Object>) root.get("api");
            cfg.api = ApiConfig.from(apiMap);

            // ── 解析 trading 节点 ──
            Map<String, Object> tradingMap = (Map<String, Object>) root.get("trading");
            cfg.trading = TradingConfig.from(tradingMap);

            // ── 解析 indicator 节点 ──
            Map<String, Object> indicatorMap = (Map<String, Object>) root.get("indicator");
            cfg.indicator = IndicatorConfig.from(indicatorMap);

            // ── 解析 backtest 节点 ──
            Map<String, Object> backtestMap = (Map<String, Object>) root.get("backtest");
            cfg.backtest = BacktestConfig.from(backtestMap);

            // ── 解析 logging 节点 ──
            Map<String, Object> loggingMap = (Map<String, Object>) root.get("logging");
            cfg.logging = LoggingConfig.from(loggingMap);

            // ── 解析 database 节点 ──
            Map<String, Object> databaseMap = (Map<String, Object>) root.get("database");
            cfg.database = DatabaseConfig.from(databaseMap);

            instance = cfg;
            log.info("配置文件加载成功，交易合约: {}", cfg.trading.getContracts());
            return cfg;

        } catch (IOException e) {
            throw new IllegalStateException("加载配置文件时发生 IO 异常", e);
        }
    }

    // ====================================================================
    //  Getter
    // ====================================================================

    public ApiConfig getApi() { return api; }
    public TradingConfig getTrading() { return trading; }
    public IndicatorConfig getIndicator() { return indicator; }
    public BacktestConfig getBacktest() { return backtest; }
    public LoggingConfig getLogging() { return logging; }
    public DatabaseConfig getDatabase() { return database; }

    // ====================================================================
    //  内部配置节点类
    // ====================================================================

    // ── API 连接配置 ──────────────────────────────────────────────────

    /** Gate.io API 连接相关配置 */
    @Data
    public static class ApiConfig {
        /** API 基础地址 */
        private String baseUrl;
        /** API Key */
        private String key;
        /** API Secret */
        private String secret;
        /** 连接超时（秒） */
        private int connectTimeoutSec;
        /** 读取超时（秒） */
        private int readTimeoutSec;
        /** 写入超时（秒） */
        private int writeTimeoutSec;

        @SuppressWarnings("unchecked")
        static ApiConfig from(Map<String, Object> m) {
            ApiConfig c = new ApiConfig();
            c.baseUrl          = getString(m, "base_url",            "https://api.gateio.ws/api/v4");

            // 优先从环境变量读取 API Key 和 Secret
            c.key              = getEnvOrConfig("GATE_API_KEY", m, "key");
            c.secret           = getEnvOrConfig("GATE_API_SECRET", m, "secret");

            c.connectTimeoutSec= getInt(m,    "connect_timeout_sec", 10);
            c.readTimeoutSec   = getInt(m,    "read_timeout_sec",    30);
            c.writeTimeoutSec  = getInt(m,    "write_timeout_sec",   30);
            return c;
        }

        /** 判断 API Key 是否已配置（非空且非占位符） */
        public boolean isConfigured() {
            return key != null && !key.isEmpty()
                    && !key.equals("YOUR_API_KEY_HERE")
                    && secret != null && !secret.isEmpty()
                    && !secret.equals("YOUR_API_SECRET_HERE");
        }
    }

    // ── 交易参数配置 ──────────────────────────────────────────────────

    /** 交易相关配置 */
    @Data
    public static class TradingConfig {
        /** 结算币种（usdt / btc） */
        private String settle;
        /** 参与交易的合约列表 */
        private List<String> contracts;
        /** 是否启用实盘下单 */
        private boolean liveTrading;
        /** 每笔开仓占账户可用余额的比例（0.0~1.0） */
        private double positionPct;
        /** 杠杆倍数 */
        private int leverage;
        /** 止损比例 */
        private double stopLossPct;
        /** 止盈比例 */
        private double takeProfitPct;
        /** 策略轮询间隔（秒） */
        private int pollIntervalSec;
        /** K 线周期 */
        private String klineInterval;
        /** 每次获取 K 线根数 */
        private int klineLimit;

        @SuppressWarnings("unchecked")
        static TradingConfig from(Map<String, Object> m) {
            TradingConfig c = new TradingConfig();
            c.settle          = getString(m, "settle",            "usdt");
            c.contracts       = (List<String>) m.getOrDefault("contracts", List.of("BTC_USDT"));
            c.liveTrading     = getBool(m,   "live_trading",      false);
            c.positionPct     = getDouble(m, "position_pct",      0.1);
            c.leverage        = getInt(m,    "leverage",          5);
            c.stopLossPct     = getDouble(m, "stop_loss_pct",     0.05);
            c.takeProfitPct   = getDouble(m, "take_profit_pct",   0.10);
            c.pollIntervalSec = getInt(m,    "poll_interval_sec", 60);
            c.klineInterval   = getString(m, "kline_interval",    "15m");
            c.klineLimit      = getInt(m,    "kline_limit",       200);
            return c;
        }
    }

    // ── 技术指标配置 ──────────────────────────────────────────────────

    /** 技术指标参数配置 */
    @Data
    public static class IndicatorConfig {
        private MacdConfig macd;
        private KdConfig kd;
        private RsiConfig rsi;

        @SuppressWarnings("unchecked")
        static IndicatorConfig from(Map<String, Object> m) {
            IndicatorConfig c = new IndicatorConfig();
            c.macd = MacdConfig.from((Map<String, Object>) m.get("macd"));
            c.kd   = KdConfig.from((Map<String, Object>) m.get("kd"));
            c.rsi  = RsiConfig.from((Map<String, Object>) m.get("rsi"));
            return c;
        }

        /** MACD 指标参数 */
        @Data
        public static class MacdConfig {
            private int fastPeriod;
            private int slowPeriod;
            private int signalPeriod;

            static MacdConfig from(Map<String, Object> m) {
                MacdConfig c = new MacdConfig();
                c.fastPeriod   = getInt(m, "fast_period",   12);
                c.slowPeriod   = getInt(m, "slow_period",   26);
                c.signalPeriod = getInt(m, "signal_period", 9);
                return c;
            }
        }

        /** KD 随机指标参数 */
        @Data
        public static class KdConfig {
            private int kPeriod;
            private int dPeriod;
            private int oversoldLevel;

            static KdConfig from(Map<String, Object> m) {
                KdConfig c = new KdConfig();
                c.kPeriod      = getInt(m, "k_period",      9);
                c.dPeriod      = getInt(m, "d_period",      3);
                c.oversoldLevel= getInt(m, "oversold_level",20);
                return c;
            }
        }

        /** RSI 指标参数 */
        @Data
        public static class RsiConfig {
            private int period;
            private int overboughtLevel;
            private int divergenceLookback;

            static RsiConfig from(Map<String, Object> m) {
                RsiConfig c = new RsiConfig();
                c.period             = getInt(m, "period",              14);
                c.overboughtLevel    = getInt(m, "overbought_level",    70);
                c.divergenceLookback = getInt(m, "divergence_lookback", 5);
                return c;
            }
        }
    }

    // ── 回测配置 ──────────────────────────────────────────────────────

    /** 回测相关配置 */
    @Data
    public static class BacktestConfig {
        /** 回测默认天数 */
        private int defaultDays;
        /** 初始资金 */
        private double initialCapital;
        /** K 线周期 */
        private String klineInterval;
        /** 手续费率 */
        private double commissionRate;

        static BacktestConfig from(Map<String, Object> m) {
            BacktestConfig c = new BacktestConfig();
            c.defaultDays    = getInt(m,    "default_days",    7);
            c.initialCapital = getDouble(m, "initial_capital", 10000.0);
            c.klineInterval  = getString(m, "kline_interval",  "15m");
            c.commissionRate = getDouble(m, "commission_rate", 0.0005);
            return c;
        }
    }

    // ── 日志配置 ──────────────────────────────────────────────────────

    /** 日志相关配置 */
    @Data
    public static class LoggingConfig {
        private String level;
        private String logDir;
        private int maxFileSizeMb;
        private int maxHistoryDays;

        static LoggingConfig from(Map<String, Object> m) {
            LoggingConfig c = new LoggingConfig();
            c.level          = getString(m, "level",           "INFO");
            c.logDir         = getString(m, "log_dir",         "logs");
            c.maxFileSizeMb  = getInt(m,    "max_file_size_mb",50);
            c.maxHistoryDays = getInt(m,    "max_history_days",30);
            return c;
        }
    }

    // ====================================================================
    //  工具方法：安全读取 Map 中的值（带默认值）
    // ====================================================================

    /**
     * 优先从环境变量读取，如果环境变量为空则回退到配置文件。
     * 如果配置值是未解析的 ${VAR:} 占位符，则视为空值。
     */
    private static String getEnvOrConfig(String envVar, Map<String, Object> m, String configKey) {
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue;
        }
        // 回退到配置文件
        String configValue = getString(m, configKey, "");
        // 检测并跳过未解析的 ${VAR:} 占位符
        if (configValue != null && configValue.matches("^\\$\\{[^}]+\\}:?.*")) {
            log.warn("配置文件中的 {} 未设置，且环境变量 {} 未定义", configKey, envVar);
            return "";
        }
        return configValue;
    }

    private static String getString(Map<String, Object> m, String key, String def) {
        return Optional.ofNullable(m)
                .map(map -> map.get(key))
                .map(Object::toString)
                .orElse(def);
    }

    private static int getInt(Map<String, Object> m, String key, int def) {
        if (m == null || !m.containsKey(key)) return def;
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
    }

    private static double getDouble(Map<String, Object> m, String key, double def) {
        if (m == null || !m.containsKey(key)) return def;
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return def; }
    }

    private static boolean getBool(Map<String, Object> m, String key, boolean def) {
        if (m == null || !m.containsKey(key)) return def;
        Object v = m.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }

    // ── 数据库配置 ──────────────────────────────────────────────────────

    /**
     * 数据库相关配置
     * 
     * 程序使用 MySQL 数据库存储交易记录、K线数据和回测报告。
     * 启动程序前请确保数据库连接配置正确，否则程序将无法启动。
     */
    @Data
    public static class DatabaseConfig {
        /** 数据库主机名或 IP 地址 */
        private String host;
        /** 数据库端口号 */
        private int port;
        /** 数据库名称（schema） */
        private String name;
        /** 数据库用户名 */
        private String username;
        /** 数据库密码 */
        private String password;
        /** 连接池配置 */
        private PoolConfig pool;

        @SuppressWarnings("unchecked")
        static DatabaseConfig from(Map<String, Object> m) {
            DatabaseConfig c = new DatabaseConfig();
            c.host = getString(m, "host", "localhost");
            c.port = getInt(m, "port", 3306);
            c.name = getString(m, "name", "quant_trader");
            c.username = getString(m, "username", "");
            c.password = getString(m, "password", "");
            c.pool = PoolConfig.from((Map<String, Object>) m.get("pool"));
            return c;
        }

        /** 连接池配置 */
        @Data
        public static class PoolConfig {
            private int minimumIdle;
            private int maximumPoolSize;
            private long maxLifetime;
            private long connectionTimeout;
            private long idleTimeout;

            static PoolConfig from(Map<String, Object> m) {
                PoolConfig c = new PoolConfig();
                c.minimumIdle = getInt(m, "minimum_idle", 5);
                c.maximumPoolSize = getInt(m, "maximum_pool_size", 20);
                c.maxLifetime = getLong(m, "max_lifetime", 1800000L);
                c.connectionTimeout = getLong(m, "connection_timeout", 30000L);
                c.idleTimeout = getLong(m, "idle_timeout", 600000L);
                return c;
            }
        }
    }

    private static long getLong(Map<String, Object> m, String key, long def) {
        if (m == null || !m.containsKey(key)) return def;
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return def; }
    }
}
