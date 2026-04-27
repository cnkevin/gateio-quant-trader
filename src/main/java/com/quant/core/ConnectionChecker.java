package com.quant.core;

import com.quant.config.AppConfig;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.GateApiException;
import io.gate.gateapi.api.FuturesApi;
import io.gate.gateapi.models.FuturesCandlestick;
import io.gate.gateapi.models.FuturesTicker;
import io.gate.gateapi.models.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Gate.io API 连接检测工具
 * <p>
 * 提供以下检测能力：
 * <ol>
 *   <li>网络连通性：能否访问 Gate.io API 端点</li>
 *   <li>公开接口：无需 Key 的行情接口是否正常</li>
 *   <li>认证接口：API Key 是否有效（查询账户/持仓）</li>
 *   <li>合约可用性：目标合约是否在交易中</li>
 * </ol>
 * </p>
 */
public class ConnectionChecker {

    private static final Logger log = LoggerFactory.getLogger(ConnectionChecker.class);

    private final AppConfig config;

    public ConnectionChecker() {
        this.config = AppConfig.getInstance();
    }

    // ====================================================================
    //  公共方法
    // ====================================================================

    /**
     * 执行完整的连接检测并打印结果到控制台
     *
     * @return true = 全部通过；false = 存在失败项
     */
    public boolean runFullCheck() {
        System.out.println();
        System.out.println("┌─────────────────────────────────────────────────┐");
        System.out.println("│            Gate.io API 连接状态检测              │");
        System.out.println("└─────────────────────────────────────────────────┘");

        boolean allPassed = true;

        // 检测1：公开行情接口
        allPassed &= checkPublicApi();

        // 检测2：API Key 配置
        allPassed &= checkApiKeyConfig();

        // 检测3：认证接口（仅在 Key 已配置时检测）
        if (config.getApi().isConfigured()) {
            allPassed &= checkAuthenticatedApi();
        }

        // 检测4：合约可用性
        for (String contract : config.getTrading().getContracts()) {
            allPassed &= checkContractAvailable(contract);
        }

        System.out.println("─────────────────────────────────────────────────");
        if (allPassed) {
            System.out.println("✅ 所有检测通过，系统就绪！");
        } else {
            System.out.println("❌ 部分检测未通过，请检查配置后重试。");
        }
        System.out.println();
        return allPassed;
    }

    // ====================================================================
    //  各项检测
    // ====================================================================

    /**
     * 检测1：公开行情接口（无需认证）
     */
    private boolean checkPublicApi() {
        System.out.print("  [1] 公开行情接口  ... ");
        try {
            List<String> contracts = config.getTrading().getContracts();
            if (contracts == null || contracts.isEmpty()) {
                System.out.println("⚠️  合约列表为空，请在配置中填写 trading.contracts");
                return false;
            }
            FuturesApi api = new FuturesApi(GateApiClientFactory.getPublicClient());
            String settle = config.getTrading().getSettle();
            String contract = contracts.get(0);

            // 获取1根K线作为连通性测试（limit=1 最省流量）
            List<FuturesCandlestick> candles = api.listFuturesCandlesticks(settle, contract)
                    .limit(1)
                    .interval("1m")
                    .execute();

            if (candles != null && !candles.isEmpty()) {
                System.out.println("✅ 正常 (最新价: " + candles.get(0).getC() + " USDT)");
                return true;
            } else {
                System.out.println("⚠️  返回数据为空");
                return false;
            }
        } catch (GateApiException e) {
            System.out.println("❌ 失败 (Gate错误: " + e.getErrorLabel() + " - " + e.getMessage() + ")");
            log.error("公开接口检测失败 (GateApiException): label={}, msg={}", e.getErrorLabel(), e.getMessage());
            return false;
        } catch (ApiException e) {
            System.out.println("❌ 失败 (HTTP " + e.getCode() + ")");
            log.error("公开接口检测失败 (ApiException): code={}", e.getCode(), e);
            return false;
        } catch (Exception e) {
            System.out.println("❌ 失败 (" + e.getMessage() + ")");
            log.error("公开接口检测失败 (未知异常)", e);
            return false;
        }
    }

    /**
     * 检测2：API Key 配置是否填写
     */
    private boolean checkApiKeyConfig() {
        System.out.print("  [2] API Key 配置  ... ");
        if (config.getApi().isConfigured()) {
            // 只显示 Key 的前4位用于确认，不显示完整 Key
            String key = config.getApi().getKey();
            String maskedKey = key.substring(0, Math.min(4, key.length())) + "****";
            System.out.println("✅ 已配置 (Key: " + maskedKey + ")");
            return true;
        } else {
            System.out.println("⚠️  未配置（请编辑 application.yml 填写 api.key 和 api.secret）");
            System.out.println("        注意：未配置 Key 时只能查看行情，无法进行交易");
            return false;
        }
    }

    /**
     * 检测3：认证接口（通过查询持仓列表验证 Key 有效性）
     */
    private boolean checkAuthenticatedApi() {
        System.out.print("  [3] 认证接口验证  ... ");
        try {
            FuturesApi api = new FuturesApi(
                    GateApiClientFactory.getAuthenticatedClient("checker"));
            String settle = config.getTrading().getSettle();

            // 查询持仓列表（不一定有持仓，但接口返回 200 即代表 Key 有效）
            List<Position> positions = api.listPositions(settle)
                    .holding(false)
                    .limit(1)
                    .execute();

            System.out.println("✅ 认证成功 (当前持仓数: " + (positions == null ? 0 : positions.size()) + ")");
            return true;

        } catch (GateApiException e) {
            // INVALID_KEY 表示 Key 错误；PERMISSION 表示权限不足
            System.out.println("❌ 失败 (Gate错误: " + e.getErrorLabel() + " - " + e.getMessage() + ")");
            log.warn("认证接口检测失败: label={}", e.getErrorLabel());
            return false;
        } catch (ApiException e) {
            System.out.println("❌ 失败 (HTTP " + e.getCode() + ")");
            log.warn("认证接口检测失败 (HTTP {})", e.getCode());
            return false;
        }
    }

    /**
     * 检测4：指定合约是否可用（能获取到行情Ticker）
     */
    private boolean checkContractAvailable(String contract) {
        System.out.print("  [4] 合约 " + String.format("%-12s", contract) + " ... ");
        try {
            FuturesApi api = new FuturesApi(GateApiClientFactory.getPublicClient());
            String settle = config.getTrading().getSettle();

            List<FuturesTicker> tickers = api.listFuturesTickers(settle)
                    .contract(contract)
                    .execute();

            if (tickers != null && !tickers.isEmpty()) {
                FuturesTicker t = tickers.get(0);
                System.out.println("✅ 可交易 (24h涨跌: " + t.getChangePercentage() + "%, "
                        + "24h成交额: " + formatBigNumber(t.getVolume24hQuote()) + " USDT)");
                return true;
            } else {
                System.out.println("⚠️  找不到合约行情，可能合约名称有误");
                return false;
            }
        } catch (Exception e) {
            System.out.println("❌ 失败 (" + e.getMessage() + ")");
            log.warn("合约 {} 可用性检测失败", contract, e);
            return false;
        }
    }

    // ====================================================================
    //  工具方法
    // ====================================================================

    /**
     * 将大数字格式化为带 K/M/B 后缀的简洁字符串
     * 例如：1234567 → "1.23M"
     */
    private String formatBigNumber(String numStr) {
        if (numStr == null) return "N/A";
        try {
            double num = Double.parseDouble(numStr);
            if (num >= 1_000_000_000) return String.format("%.2fB", num / 1_000_000_000);
            if (num >= 1_000_000)     return String.format("%.2fM", num / 1_000_000);
            if (num >= 1_000)         return String.format("%.2fK", num / 1_000);
            return numStr;
        } catch (NumberFormatException e) {
            return numStr;
        }
    }
}
