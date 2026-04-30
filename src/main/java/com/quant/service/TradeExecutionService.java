package com.quant.service;

import com.quant.config.AppConfig;
import com.quant.core.GateApiClientFactory;
import com.quant.database.TradingRepository;
import com.quant.model.TradingRecord;
import com.quant.model.TradeSignal.Signal;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.GateApiException;
import io.gate.gateapi.api.FuturesApi;
import io.gate.gateapi.models.FuturesAccount;
import io.gate.gateapi.models.FuturesOrder;
import io.gate.gateapi.models.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 合约交易执行服务
 * <p>
 * 负责：
 * <ul>
 *   <li>根据信号执行市价开仓/平仓</li>
 *   <li>设置/更新杠杆</li>
 *   <li>查询当前持仓状态</li>
 *   <li>计算开仓数量（基于账户余额和配置比例）</li>
 * </ul>
 * </p>
 *
 * <h3>注意事项：</h3>
 * <ul>
 *   <li>当 {@code liveTrading=false} 时，所有下单操作只打印日志，不实际调用 API</li>
 *   <li>合约 size 表示张数，每张合约面值可在合约详情中查到</li>
 * </ul>
 */
public class TradeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TradeExecutionService.class);
    private static final Logger tradeLog = LoggerFactory.getLogger("TRADE_SIGNAL");

    private final AppConfig config;
    private final AppConfig.TradingConfig tradingCfg;
    private final String settle;
    private final TradingRepository tradingRepo;

    public TradeExecutionService() {
        this.config     = AppConfig.getInstance();
        this.tradingCfg = config.getTrading();
        this.settle     = tradingCfg.getSettle();
        this.tradingRepo = new TradingRepository();
    }

    // ====================================================================
    //  核心执行入口
    // ====================================================================

    /**
     * 根据信号执行交易操作
     *
     * @param signal     来自策略的交易信号
     * @param contract   合约名称
     */
    public void executeSignal(Signal signal, String contract) {
        if (signal == null || !signal.isActionable()) return;

        switch (signal.getType()) {
            case BUY:
                openLongPosition(contract, signal.getPrice());
                break;
            case SELL:
            case STOP_LOSS:
            case TAKE_PROFIT:
                closeLongPosition(contract, signal);
                break;
            default:
                break;
        }
    }

    // ====================================================================
    //  开仓操作
    // ====================================================================

    /**
     * 开多仓（市价单）
     *
     * @param contract     合约名称（如 BTC_USDT）
     * @param currentPrice 当前价格（用于计算开仓数量）
     */
    public void openLongPosition(String contract, double currentPrice) {
        log.info("▶ 准备开多仓 合约={}, 参考价格={}", contract, currentPrice);

        try {
            // 1. 设置杠杆
            setLeverage(contract, tradingCfg.getLeverage());

            // 2. 计算开仓数量
            long size = calcOpenSize(contract, currentPrice);
            if (size <= 0) {
                log.warn("计算出的开仓数量为0，跳过开仓 合约={}", contract);
                return;
            }

            // 3. 构建市价开仓订单（size > 0 = 开多仓）
            FuturesOrder order = new FuturesOrder();
            order.setContract(contract);
            order.setSize(String.valueOf(size));  // gate-api 7.2.57: size 改为 String 类型
            // 市价单：price="0", tif=IOC
            order.setPrice("0");
            order.setTif(FuturesOrder.TifEnum.IOC);
            order.setReduceOnly(false);
            order.setClose(false);
            order.setText("t-quant-open");  // 订单标注，方便在官网区分

            if (!tradingCfg.isLiveTrading()) {
                // 模拟模式：只打印不下单
                log.info("📌 [模拟模式] 开多仓 合约={} 数量={}张 价格=市价", contract, size);
                tradeLog.info("MOCK_BUY | {} | 数量={}张 | 参考价={}", contract, size, currentPrice);
                return;
            }

            // 实盘下单
            FuturesApi api = new FuturesApi(GateApiClientFactory.getAuthenticatedClient(contract));

            // 创建交易记录
            String orderId = "PENDING_" + System.currentTimeMillis();
            TradingRecord record = new TradingRecord(orderId, contract, "BUY", TradingRecord.DIR_OPEN_LONG);
            record.setSize(java.math.BigDecimal.valueOf(size));
            record.setPrice(java.math.BigDecimal.valueOf(currentPrice));
            record.setOrderType("MARKET");
            tradingRepo.save(record);

            // Gate API v7.x: createFuturesOrder(settle, order, xGateExptime) - 第三个参数可传 null
            FuturesOrder result = api.createFuturesOrder(settle, order, null);

            // 更新交易记录
            record.setOrderId(String.valueOf(result.getId()));
            record.setPrice(java.math.BigDecimal.valueOf(Double.parseDouble(result.getFillPrice())));
            record.setStatus(TradingRecord.STATUS_SUCCESS);
            record.setOrderTime(java.time.LocalDateTime.now());
            record.setFillTime(java.time.LocalDateTime.now());
            tradingRepo.save(record);

            log.info("✅ 开多仓成功 合约={} 订单ID={} 数量={}张 成交均价={}",
                    contract, result.getId(), result.getSize(), result.getFillPrice());
            tradeLog.info("BUY_EXECUTED | {} | 订单ID={} | 数量={}张 | 均价={}",
                    contract, result.getId(), result.getSize(), result.getFillPrice());

        } catch (GateApiException e) {
            log.error("开多仓失败(Gate业务错误) 合约={}, label={}, msg={}",
                    contract, e.getErrorLabel(), e.getMessage(), e);
            // 更新交易记录状态
            tradingRepo.updateStatus("PENDING_" + System.currentTimeMillis(),
                    TradingRecord.STATUS_FAILED, e.getErrorLabel());
        } catch (ApiException e) {
            log.error("开多仓失败(HTTP错误) 合约={}, code={}", contract, e.getCode(), e);
        } catch (Exception e) {
            log.error("开多仓失败(未知错误) 合约={}", contract, e);
        }
    }

    // ====================================================================
    //  平仓操作
    // ====================================================================

    /**
     * 平多仓（市价单，全部平仓）
     *
     * @param contract 合约名称
     * @param signal   触发平仓的信号（用于记录日志）
     */
    public void closeLongPosition(String contract, Signal signal) {
        log.info("▶ 准备平多仓 合约={} 原因={}", contract, signal.getReason());

        try {
            // 查询当前持仓数量
            Position pos = getPosition(contract);
            if (pos == null || pos.getSize() == null || "0".equals(pos.getSize())) {
                log.warn("当前无持仓，跳过平仓 合约={}", contract);
                return;
            }

            long holdSize = Long.parseLong(pos.getSize());  // gate-api 7.2.57: size 改为 String 类型
            if (holdSize <= 0) {
                log.warn("当前持仓方向不是多仓（size={}），跳过平仓 合约={}", holdSize, contract);
                return;
            }

            // 构建市价平仓订单（size = 负持仓量 = 平多）
            FuturesOrder order = new FuturesOrder();
            order.setContract(contract);
            order.setSize(String.valueOf(-holdSize));  // gate-api 7.2.57: size 改为 String 类型
            order.setPrice("0");
            order.setTif(FuturesOrder.TifEnum.IOC);
            order.setReduceOnly(true);       // 只减仓，不反手开空
            order.setClose(false);
            order.setText("t-quant-close");

            if (!tradingCfg.isLiveTrading()) {
                log.info("📌 [模拟模式] 平多仓 合约={} 数量={}张 类型={}",
                        contract, holdSize, signal.getType().getLabel());
                tradeLog.info("MOCK_SELL | {} | 数量={}张 | 信号类型={} | 原因={}",
                        contract, holdSize, signal.getType().getLabel(), signal.getReason());
                return;
            }

            FuturesApi api = new FuturesApi(GateApiClientFactory.getAuthenticatedClient(contract));

            // Gate API v7.x: createFuturesOrder(settle, order, xGateExptime) - 第三个参数可传 null
            FuturesOrder result = api.createFuturesOrder(settle, order, null);

            // 成交后再创建交易记录（此时才有真实成交价）
            String orderId = String.valueOf(result.getId());
            TradingRecord record = new TradingRecord(orderId, contract,
                    signal.getType().name(), TradingRecord.DIR_CLOSE_LONG);
            record.setSignalReason(signal.getReason());
            record.setSize(java.math.BigDecimal.valueOf(Math.abs(Long.parseLong(result.getSize()))));
            record.setPrice(java.math.BigDecimal.valueOf(Double.parseDouble(result.getFillPrice())));
            record.setOrderType("MARKET");
            record.setStatus(TradingRecord.STATUS_SUCCESS);
            record.setOrderTime(java.time.LocalDateTime.now());
            record.setFillTime(java.time.LocalDateTime.now());
            tradingRepo.save(record);

            log.info("✅ 平多仓成功 合约={} 订单ID={} 平仓数量={} 均价={} 信号类型={}",
                    contract, result.getId(), result.getSize(),
                    result.getFillPrice(), signal.getType().getLabel());
            tradeLog.info("SELL_EXECUTED | {} | 订单ID={} | 数量={} | 均价={} | 信号={}",
                    contract, result.getId(), result.getSize(),
                    result.getFillPrice(), signal.getType().getLabel());

        } catch (GateApiException e) {
            log.error("平多仓失败(Gate业务错误) 合约={}, label={}", contract, e.getErrorLabel(), e);
        } catch (ApiException e) {
            log.error("平多仓失败(HTTP错误) 合约={}, code={}", contract, e.getCode(), e);
        } catch (Exception e) {
            log.error("平多仓失败(未知错误) 合约={}", contract, e);
        }
    }

    // ====================================================================
    //  持仓查询
    // ====================================================================

    /**
     * 查询指定合约的当前持仓
     *
     * @param contract 合约名称
     * @return Position 对象，若查询失败或无持仓则返回 null
     */
    public Position getPosition(String contract) {
        try {
            FuturesApi api = new FuturesApi(GateApiClientFactory.getAuthenticatedClient(contract));
            // ⚠️ Gate.io API 服务端 BUG：无持仓时 GET /futures/usdt/positions/{contract}
            // 返回空数组 [] 而非 Position 对象，导致 Gson 抛出 JsonSyntaxException。
            // 改回 listPositions 遍历方案规避此问题。
            java.util.List<Position> positions = api.listPositions(settle).execute();
            if (positions == null || positions.isEmpty()) {
                return null;
            }
            for (Position pos : positions) {
                if (contract.equals(pos.getContract())) {
                    return pos;
                }
            }
            return null;
        } catch (GateApiException e) {
            if ("POSITION_NOT_FOUND".equals(e.getErrorLabel())) {
                return null;
            }
            log.error("查询持仓失败(Gate错误) 合约={}, label={}", contract, e.getErrorLabel());
            return null;
        } catch (ApiException e) {
            log.error("查询持仓失败(HTTP错误) 合约={}, code={}", contract, e.getCode());
            return null;
        }
    }

    /**
     * 判断指定合约是否有多仓
     *
     * @param contract 合约名称
     * @return true = 有多仓
     */
    public boolean hasLongPosition(String contract) {
        Position pos = getPosition(contract);
        return pos != null && pos.getSize() != null && Long.parseLong(pos.getSize()) > 0;
    }

    /**
     * 获取当前持仓的开仓均价
     *
     * @param contract 合约名称
     * @return 开仓均价；若无持仓则返回 0
     */
    public double getEntryPrice(String contract) {
        Position pos = getPosition(contract);
        if (pos == null || pos.getEntryPrice() == null) return 0.0;
        try {
            return Double.parseDouble(pos.getEntryPrice());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ====================================================================
    //  账户与杠杆
    // ====================================================================

    /**
     * 获取合约账户可用余额（USDT）
     *
     * @return 可用余额；若查询失败则返回 0
     */
    public double getAvailableBalance() {
        try {
            FuturesApi api = new FuturesApi(GateApiClientFactory.getAuthenticatedClient("account"));
            // Gate API v7.x: listFuturesAccounts(settle) 返回单个 FuturesAccount
            FuturesAccount account = api.listFuturesAccounts(settle);
            if (account != null && account.getAvailable() != null) {
                return Double.parseDouble(account.getAvailable());
            }
        } catch (Exception e) {
            log.error("查询账户余额失败", e);
        }
        return 0.0;
    }

    /**
     * 设置合约杠杆倍数
     * <p>
     * 使用 PositionApi 而非 FuturesApi 来设置杠杆，因为 Gate API 返回的是持仓列表格式。
     * </p>
     *
     * @param contract 合约名称
     * @param leverage 杠杆倍数
     */
    public void setLeverage(String contract, int leverage) {
        try {
            FuturesApi api = new FuturesApi(GateApiClientFactory.getAuthenticatedClient(contract));

            // Gate API v4: PUT /futures/usdt/positions/{contract}/leverage
            // 参数：settle, contract, leverage, crossLeverageLimit, pid
            // - 逐仓模式：leverage = 具体值, crossLeverageLimit = null, pid = null
            // - 全仓模式：leverage = "0", crossLeverageLimit = 具体值, pid = null
            api.updatePositionLeverage(settle, contract, String.valueOf(leverage), null, null);
            log.info("杠杆设置成功 合约={} 杠杆={}x", contract, leverage);
        } catch (GateApiException e) {
            log.warn("设置杠杆失败(Gate业务错误) 合约={} 错误={}", contract, e.getErrorLabel());
        } catch (Exception e) {
            log.warn("设置杠杆失败(未知错误) 合约={}", contract, e);
        }
    }

    // ====================================================================
    //  数量计算
    // ====================================================================

    /**
     * 根据账户余额和配置计算开仓张数
     * <p>
     * 计算公式：
     * <pre>
     * 开仓金额 = 可用余额 × position_pct
     * 开仓张数 = floor(开仓金额 × 杠杆 / 当前价格)
     * </pre>
     * </p>
     *
     * @param contract     合约名称
     * @param currentPrice 当前价格
     * @return 开仓张数（至少为 1）
     */
    private long calcOpenSize(String contract, double currentPrice) {
        if (currentPrice <= 0) {
            log.error("calcOpenSize: currentPrice={} 非法（必须>0），跳过开仓 合约={}", currentPrice, contract);
            return 0;
        }
        double balance    = getAvailableBalance();
        double useAmount  = balance * tradingCfg.getPositionPct();
        double leveraged  = useAmount * tradingCfg.getLeverage();
        long   size       = (long) Math.floor(leveraged / currentPrice);

        log.info("开仓数量计算 合约={} 余额={} 使用比例={} 杠杆={}x 参考价={} 计算张数={}",
                contract, String.format("%.2f", balance),
                String.format("%.0f%%", tradingCfg.getPositionPct() * 100),
                tradingCfg.getLeverage(), currentPrice, size);

        return Math.max(size, 1L);  // 至少开 1 张
    }
}
