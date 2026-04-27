package com.quant.core;

import com.quant.config.AppConfig;
import io.gate.gateapi.ApiClient;
import io.gate.gateapi.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Gate.io ApiClient 工厂类
 * <p>
 * 统一管理 ApiClient 的创建与复用。
 * 每个线程（合约）使用独立的 ApiClient 实例，避免多线程竞争问题。
 * </p>
 *
 * <pre>
 * 使用方式：
 *   ApiClient client = GateApiClientFactory.getClient("BTC_USDT");
 *   FuturesApi api   = new FuturesApi(client);
 * </pre>
 */
public class GateApiClientFactory {

    private static final Logger log = LoggerFactory.getLogger(GateApiClientFactory.class);

    /**
     * 每个合约（线程）独立的 ApiClient 缓存
     * Key: 合约名称（如 "BTC_USDT"）或固定 key "default"
     */
    private static final ConcurrentMap<String, ApiClient> CLIENT_CACHE = new ConcurrentHashMap<>();

    /** 私有构造，禁止实例化 */
    private GateApiClientFactory() {}

    // ====================================================================
    //  公共方法
    // ====================================================================

    /**
     * 获取带认证的 ApiClient（用于需要 API Key 的私有接口）。
     * 同一个 key 只会创建一次实例（线程安全）。
     *
     * @param clientKey 客户端标识，通常使用合约名称或线程名
     * @return 已配置 API Key 的 ApiClient 实例
     */
    public static ApiClient getAuthenticatedClient(String clientKey) {
        return CLIENT_CACHE.computeIfAbsent(clientKey + "_auth", k -> {
            AppConfig.ApiConfig apiCfg = AppConfig.getInstance().getApi();
            ApiClient client = buildBaseClient(apiCfg);
            // 设置 API Key 和 Secret（Gate.io APIv4 认证）
            client.setApiKeySecret(apiCfg.getKey(), apiCfg.getSecret());
            log.debug("创建带认证的 ApiClient，key={}", clientKey);
            return client;
        });
    }

    /**
     * 获取无认证的 ApiClient（用于公开行情接口）。
     * 公开接口不需要 API Key，使用同一个共享实例。
     *
     * @return 无认证的 ApiClient 实例
     */
    public static ApiClient getPublicClient() {
        return CLIENT_CACHE.computeIfAbsent("public", k -> {
            AppConfig.ApiConfig apiCfg = AppConfig.getInstance().getApi();
            log.debug("创建公开 ApiClient（无认证）");
            return buildBaseClient(apiCfg);
        });
    }

    /**
     * 移除指定 key 的客户端缓存（用于重新加载配置等场景）
     *
     * @param clientKey 客户端标识
     */
    public static void evict(String clientKey) {
        CLIENT_CACHE.remove(clientKey + "_auth");
        log.info("已移除 ApiClient 缓存，key={}", clientKey);
    }

    /**
     * 清空所有缓存（重启/重连时使用）
     */
    public static void evictAll() {
        CLIENT_CACHE.clear();
        log.info("已清空所有 ApiClient 缓存");
    }

    // ====================================================================
    //  私有方法
    // ====================================================================

    /**
     * 构建基础 ApiClient，设置 baseUrl 和超时参数
     */
    private static ApiClient buildBaseClient(AppConfig.ApiConfig cfg) {
        ApiClient client = new ApiClient();
        client.setBasePath(cfg.getBaseUrl());

        // 设置 HTTP 超时（毫秒 → 直接使用秒，SDK 内部转换）
        // Gate API SDK 底层使用 OkHttp，单位为秒
        client.setConnectTimeout(cfg.getConnectTimeoutSec() * 1000);
        client.setReadTimeout(cfg.getReadTimeoutSec() * 1000);
        client.setWriteTimeout(cfg.getWriteTimeoutSec() * 1000);

        return client;
    }
}
