package com.quant.service;

import com.quant.config.AppConfig;
import com.quant.core.GateApiClientFactory;
import com.quant.model.Candlestick;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.GateApiException;
import io.gate.gateapi.api.FuturesApi;
import io.gate.gateapi.models.FuturesCandlestick;
import io.gate.gateapi.models.FuturesTicker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 市场数据服务
 * <p>
 * 封装对 Gate.io 行情接口的调用，提供：
 * <ul>
 *   <li>K 线数据获取（按时间范围或根数）</li>
 *   <li>最新行情 Ticker 查询</li>
 *   <li>API 调用异常的统一处理与重试</li>
 * </ul>
 * </p>
 *
 * <pre>
 * Gate.io K线接口支持的周期：
 *   10s, 30s, 1m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 8h, 12h, 1d, 7d, 30d
 * </pre>
 */
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    /** 最大重试次数（遇到网络波动时自动重试） */
    private static final int MAX_RETRIES = 3;

    /** 重试间隔（毫秒） */
    private static final long RETRY_DELAY_MS = 2000;

    private final AppConfig config;
    private final FuturesApi futuresApi;
    private final String settle;

    public MarketDataService() {
        this.config    = AppConfig.getInstance();
        this.settle    = config.getTrading().getSettle();
        // 市场数据接口为公开接口，无需认证
        this.futuresApi = new FuturesApi(GateApiClientFactory.getPublicClient());
    }

    // ====================================================================
    //  K 线数据获取
    // ====================================================================

    /**
     * 获取最新的 N 根 K 线（倒序返回，最新的在最前面，方法内部会转为正序）
     *
     * @param contract K 线周期（如 "BTC_USDT"）
     * @param interval K 线周期（如 "15m"）
     * @param limit    获取根数（最大 2000）
     * @return 正序排列的 K 线列表（最旧的在 index 0）
     */
    public List<Candlestick> getLatestKlines(String contract, String interval, int limit) {
        log.debug("获取K线 合约={}, 周期={}, 数量={}", contract, interval, limit);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                List<FuturesCandlestick> raw = futuresApi
                        .listFuturesCandlesticks(settle, contract)
                        .interval(interval)
                        .limit(limit)
                        .execute();

                if (raw == null || raw.isEmpty()) {
                    log.warn("K线数据为空 合约={}, 尝试={}/{}", contract, attempt, MAX_RETRIES);
                    return Collections.emptyList();
                }

                // 将 SDK 模型转换为内部模型，并按时间正序排列
                List<Candlestick> result = raw.stream()
                        .map(Candlestick::fromSdk)
                        .sorted((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                        .collect(Collectors.toList());

                log.debug("K线获取成功 合约={}, 实际数量={}, 最新收盘价={}",
                        contract, result.size(), result.get(result.size() - 1).getClose());
                return result;

            } catch (GateApiException e) {
                // Gate.io 业务错误（如合约不存在、超出限流等）
                log.error("获取K线业务错误 合约={}, label={}, msg={}, 尝试={}/{}",
                        contract, e.getErrorLabel(), e.getMessage(), attempt, MAX_RETRIES);
                // 业务错误不重试（参数错了重试也没用）
                return Collections.emptyList();

            } catch (ApiException e) {
                log.warn("获取K线网络错误 合约={}, HTTP={}, 尝试={}/{}",
                        contract, e.getCode(), attempt, MAX_RETRIES);
                if (attempt < MAX_RETRIES) {
                    sleepQuietly(RETRY_DELAY_MS * attempt); // 退避重试
                }
            } catch (Exception e) {
                log.error("获取K线未知错误 合约={}, 错误={}, 尝试={}/{}",
                        contract, e.getMessage(), attempt, MAX_RETRIES, e);
                if (attempt < MAX_RETRIES) {
                    sleepQuietly(RETRY_DELAY_MS);
                }
            }
        }

        log.error("获取K线失败，已重试{}次 合约={}", MAX_RETRIES, contract);
        return Collections.emptyList();
    }

    /**
     * 获取指定时间范围内的 K 线数据
     *
     * @param contract  合约名称
     * @param interval  K 线周期
     * @param fromEpoch 开始时间（Unix 秒，含）
     * @param toEpoch   结束时间（Unix 秒，含）
     * @return 正序排列的 K 线列表
     */
    public List<Candlestick> getKlinesByTimeRange(String contract, String interval,
                                                   long fromEpoch, long toEpoch) {
        log.debug("获取时间范围K线 合约={}, 周期={}, from={}, to={}",
                contract, interval, fromEpoch, toEpoch);

        try {
            // Gate.io API: limit 和 from/to 不能同时使用
            // 当使用 from/to 时，API 返回该时间段内的所有数据（最多2000根）
            List<FuturesCandlestick> raw = futuresApi
                    .listFuturesCandlesticks(settle, contract)
                    .interval(interval)
                    .from(fromEpoch)
                    .to(toEpoch)
                    .execute();

            if (raw == null || raw.isEmpty()) {
                log.warn("时间范围K线数据为空 合约={}", contract);
                return Collections.emptyList();
            }

            List<Candlestick> result = raw.stream()
                    .map(Candlestick::fromSdk)
                    .sorted((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                    .collect(Collectors.toList());

            log.info("时间范围K线获取成功 合约={}, 数量={}", contract, result.size());
            return result;

        } catch (GateApiException e) {
            log.error("时间范围K线业务错误 合约={}, label={}", contract, e.getErrorLabel(), e);
            return Collections.emptyList();
        } catch (ApiException e) {
            log.error("时间范围K线网络错误 合约={}, HTTP={}", contract, e.getCode(), e);
            return Collections.emptyList();
        }
    }

    // ====================================================================
    //  行情 Ticker 查询
    // ====================================================================

    /**
     * 获取指定合约的最新行情 Ticker
     *
     * @param contract 合约名称
     * @return Ticker 对象；若失败则返回 null
     */
    public FuturesTicker getTicker(String contract) {
        try {
            List<FuturesTicker> tickers = futuresApi.listFuturesTickers(settle)
                    .contract(contract)
                    .execute();

            if (tickers != null && !tickers.isEmpty()) {
                return tickers.get(0);
            }
        } catch (GateApiException e) {
            log.error("获取Ticker业务错误 合约={}, label={}", contract, e.getErrorLabel());
        } catch (ApiException e) {
            log.error("获取Ticker网络错误 合约={}, HTTP={}", contract, e.getCode());
        }
        return null;
    }

    /**
     * 获取最新成交价格
     *
     * @param contract 合约名称
     * @return 最新价格；若失败则返回 0
     */
    public double getLatestPrice(String contract) {
        FuturesTicker ticker = getTicker(contract);
        if (ticker == null || ticker.getLast() == null) return 0.0;
        try {
            return Double.parseDouble(ticker.getLast());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ====================================================================
    //  工具方法
    // ====================================================================

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
