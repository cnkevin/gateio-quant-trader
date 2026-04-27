package com.quant.engine;

import com.quant.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 多合约管理器
 * <p>
 * 统一管理多个 {@link ContractTradeEngine} 实例的生命周期，
 * 支持同时运行多个合约的策略。
 * </p>
 */
public class TradingEngineManager {

    private static final Logger log = LoggerFactory.getLogger(TradingEngineManager.class);

    /** 合约名称 → 引擎实例的映射表 */
    private final ConcurrentMap<String, ContractTradeEngine> engines = new ConcurrentHashMap<>();

    private final AppConfig config;

    public TradingEngineManager() {
        this.config = AppConfig.getInstance();
    }

    // ====================================================================
    //  生命周期管理
    // ====================================================================

    /**
     * 根据配置文件启动所有合约的交易引擎
     */
    public void startAll() {
        List<String> contracts = config.getTrading().getContracts();
        if (contracts == null || contracts.isEmpty()) {
            log.warn("配置中没有指定任何合约，请检查 application.yml");
            return;
        }

        log.info("准备启动 {} 个合约的交易引擎: {}", contracts.size(), contracts);
        for (String contract : contracts) {
            startContract(contract);
        }
        log.info("所有合约引擎已启动！");
    }

    /**
     * 启动指定合约的交易引擎
     *
     * @param contract 合约名称
     */
    public void startContract(String contract) {
        if (engines.containsKey(contract) && engines.get(contract).isRunning()) {
            log.warn("合约引擎已在运行 合约={}", contract);
            return;
        }
        ContractTradeEngine engine = new ContractTradeEngine(contract);
        engines.put(contract, engine);
        engine.start();
    }

    /**
     * 停止指定合约的交易引擎
     *
     * @param contract 合约名称
     */
    public void stopContract(String contract) {
        ContractTradeEngine engine = engines.get(contract);
        if (engine != null) {
            engine.stop();
            log.info("已发送停止信号 合约={}", contract);
        } else {
            log.warn("找不到指定合约的引擎 合约={}", contract);
        }
    }

    /**
     * 停止所有合约的交易引擎
     */
    public void stopAll() {
        log.info("停止所有合约引擎...");
        for (ContractTradeEngine engine : engines.values()) {
            engine.stop();
        }
        engines.clear();
        log.info("所有合约引擎已停止");
    }

    // ====================================================================
    //  状态查询
    // ====================================================================

    /**
     * 获取所有引擎的运行状态报告
     *
     * @return 状态信息列表
     */
    public List<String> getStatusReport() {
        if (engines.isEmpty()) return Collections.singletonList("当前无运行中的合约引擎");

        List<String> report = new ArrayList<>();
        report.add(String.format("共 %d 个合约引擎：", engines.size()));
        for (ContractTradeEngine engine : engines.values()) {
            report.add(String.format("  %-15s  %s",
                    engine.getContract(),
                    engine.isRunning() ? "🟢 运行中" : "🔴 已停止"));
        }
        return report;
    }

    /** 判断是否有任何引擎在运行 */
    public boolean isAnyRunning() {
        return engines.values().stream().anyMatch(ContractTradeEngine::isRunning);
    }
}
