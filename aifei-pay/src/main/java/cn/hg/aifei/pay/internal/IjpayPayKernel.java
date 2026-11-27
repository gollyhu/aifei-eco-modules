/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import com.alipay.v3.ApiClient;
import com.alipay.v3.model.AlipayTradePrecreateModel;
import com.alipay.v3.model.AlipayTradeQueryModel;
import com.alipay.v3.model.AlipayTradeRefundModel;
import com.ijpay.alipay.v3.AliPayTradeApi;
import com.ijpay.alipay.v3.enums.AliPayMethodEnum;
import java.util.Map;

/**
 * 内核边界的唯一实现：全模块唯一出现 IJPay 交易接口与开放平台 SDK 交易模型的位置。
 *
 * <p>页面类下单**必须**走实例化的 {@code GenericExecuteApi}，不得走 {@code AliPayApi} 的静态方法——
 * 后者共用进程级默认客户端，多应用会互相覆盖。</p>
 *
 * <p>本类不收敛异常：内核异常原样上抛，由 {@link PayErrors} 在调用边界统一转换。</p>
 */
public final class IjpayPayKernel implements PayKernel {

    /**
     * 报文键：商户订单号。
     */
    public static final String PARAM_OUT_TRADE_NO = "out_trade_no";

    /**
     * 报文键：订单金额（元）。
     */
    public static final String PARAM_TOTAL_AMOUNT = "total_amount";

    /**
     * 报文键：订单标题。
     */
    public static final String PARAM_SUBJECT = "subject";

    /**
     * 报文键：异步回调地址。
     */
    public static final String PARAM_NOTIFY_URL = "notify_url";

    /**
     * 报文键：同步跳转地址。
     */
    public static final String PARAM_RETURN_URL = "return_url";

    /**
     * 报文键：业务参数容器。
     */
    public static final String PARAM_BIZ_CONTENT = "biz_content";

    /**
     * 报文键：产品码。
     */
    public static final String PARAM_PRODUCT_CODE = "product_code";

    /**
     * 报文键：退出地址（H5 支付）。
     */
    public static final String PARAM_QUIT_URL = "quit_url";

    /**
     * 报文键：支付宝交易号。
     */
    public static final String PARAM_TRADE_NO = "trade_no";

    /**
     * 报文键：退款请求号（幂等键）。
     */
    public static final String PARAM_OUT_REQUEST_NO = "out_request_no";

    /**
     * 报文键：退款金额（元）。
     */
    public static final String PARAM_REFUND_AMOUNT = "refund_amount";

    /**
     * 报文键：退款原因。
     */
    public static final String PARAM_REFUND_REASON = "refund_reason";

    private final PayClients clients;

    /**
     * @param clients 客户端构建与缓存
     */
    public IjpayPayKernel(PayClients clients) {
        if (clients == null) {
            throw new IllegalArgumentException("clients 不能为 null");
        }
        this.clients = clients;
    }

    /**
     * 当面付扫码预下单（{@code alipay.trade.precreate}）。
     *
     * @param app       应用配置
     * @param bizParams 报文参数
     * @return 内核响应对象，由 {@link PayResults} 转换
     * @throws Exception 内核异常（由 {@link PayErrors} 收敛）
     */
    @Override
    public Object precreate(PayAppConfig app, Map<String, Object> bizParams) throws Exception {
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(string(bizParams, PARAM_OUT_TRADE_NO));
        model.setTotalAmount(string(bizParams, PARAM_TOTAL_AMOUNT));
        model.setSubject(string(bizParams, PARAM_SUBJECT));
        model.setNotifyUrl(string(bizParams, PARAM_NOTIFY_URL));
        model.setProductCode(string(bizParams, PARAM_PRODUCT_CODE));
        return tradeApi(app).precreate(model);
    }

    /**
     * 页面类下单（{@code alipay.trade.wap.pay} / {@code alipay.trade.page.pay}）。
     *
     * @param app        应用配置
     * @param method     支付宝接口方法名
     * @param httpMethod {@code GET} 返回跳转 URL，{@code POST} 返回自提交表单 HTML
     * @param bizParams  报文参数
     * @return 载荷字符串
     * @throws Exception 内核异常（由 {@link PayErrors} 收敛）
     */
    @Override
    public String pagePay(PayAppConfig app, String method, String httpMethod, Map<String, Object> bizParams)
            throws Exception {
        AliPayMethodEnum payMethod = AliPayMethodEnum.of(method);
        if (payMethod == null) {
            throw new IllegalArgumentException("未知的页面下单接口方法名：" + method);
        }
        return clients.pageApi(app.appId()).pageExecute(payMethod.getValue(), httpMethod, bizParams);
    }

    /**
     * 查单（{@code alipay.trade.query}）。
     *
     * @param app       应用配置
     * @param bizParams 报文参数
     * @return 内核响应对象，由 {@link PayResults} 转换
     * @throws Exception 内核异常（由 {@link PayErrors} 收敛）
     */
    @Override
    public Object query(PayAppConfig app, Map<String, Object> bizParams) throws Exception {
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(string(bizParams, PARAM_OUT_TRADE_NO));
        model.setTradeNo(string(bizParams, PARAM_TRADE_NO));
        return tradeApi(app).query(model);
    }

    /**
     * 退款（{@code alipay.trade.refund}）。
     *
     * @param app       应用配置
     * @param bizParams 报文参数（{@code out_request_no} 为幂等键，原样透传）
     * @return 内核响应对象，由 {@link PayResults} 转换
     * @throws Exception 内核异常（由 {@link PayErrors} 收敛）
     */
    @Override
    public Object refund(PayAppConfig app, Map<String, Object> bizParams) throws Exception {
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(string(bizParams, PARAM_OUT_TRADE_NO));
        model.setTradeNo(string(bizParams, PARAM_TRADE_NO));
        model.setOutRequestNo(string(bizParams, PARAM_OUT_REQUEST_NO));
        model.setRefundAmount(string(bizParams, PARAM_REFUND_AMOUNT));
        model.setRefundReason(string(bizParams, PARAM_REFUND_REASON));
        return tradeApi(app).refund(model);
    }

    /**
     * 构造该应用独占的交易接口实例（构造注入，天然多应用隔离）。
     *
     * @param app 应用配置
     * @return 交易接口
     */
    private AliPayTradeApi tradeApi(PayAppConfig app) {
        ApiClient apiClient = clients.apiClient(app.appId());
        return new AliPayTradeApi(apiClient);
    }

    /**
     * 取报文参数为字符串。
     *
     * @param bizParams 报文参数
     * @param key       键名
     * @return 值；缺失为 {@code null}
     */
    private static String string(Map<String, Object> bizParams, String key) {
        Object value = bizParams.get(key);
        return value == null ? null : value.toString();
    }
}
