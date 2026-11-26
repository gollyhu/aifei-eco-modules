/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import cn.hg.aifei.pay.PayClient;
import cn.hg.aifei.pay.PayException;
import cn.hg.aifei.pay.model.PayOrderResult;
import cn.hg.aifei.pay.model.PayQueryResult;
import cn.hg.aifei.pay.model.PayRefundResult;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link PayClient} 的唯一实现：参数校验 + 编排 + 异常收敛。
 *
 * <p>每个方法的骨架固定为：探测内核依赖 → 校验调用方参数 → 解析应用配置 → 回调地址回退 →
 * 组报文 → 调内核 → 转换值对象；内核异常一律经 {@link PayErrors} 收敛，原始类型只作 {@code cause}。</p>
 */
public final class DefaultPayClient implements PayClient {

    /**
     * 页面下单接口方法名：手机网站支付。
     */
    private static final String METHOD_WAP_PAY = "alipay.trade.wap.pay";

    /**
     * 页面下单接口方法名：电脑网站支付。
     */
    private static final String METHOD_PAGE_PAY = "alipay.trade.page.pay";

    /**
     * 产品码：手机网站支付。
     */
    private static final String PRODUCT_CODE_WAP = "QUICK_WAP_WAY";

    /**
     * 产品码：电脑网站支付。
     */
    private static final String PRODUCT_CODE_PAGE = "FAST_INSTANT_TRADE_PAY";

    /**
     * 产品码：当面付。
     */
    private static final String PRODUCT_CODE_FACE_TO_FACE = "FACE_TO_FACE_PAYMENT";

    /**
     * 商户订单号与退款请求号的长度上限。
     */
    private static final int MAX_TRADE_NO_LENGTH = 64;

    /**
     * 订单标题的长度上限。
     */
    private static final int MAX_SUBJECT_LENGTH = 256;

    private final PayConfig config;

    private final PayKernel kernel;

    private final String channelName;

    private final boolean v2;

    /**
     * 测试夹具快捷构造：使用默认支付宝 V3 通道标识。
     */
    DefaultPayClient(PayConfig config, PayKernel kernel) {
        this(config, kernel, "alipay3", false);
    }

    /**
     * 供单测注入伪内核（{@link PayKernel} 是包级类型，故本构造器不加 {@code public}）。
     *
     * @param config      配置域
     * @param kernel      内核边界
     * @param channelName 通道标识
     */
    DefaultPayClient(PayConfig config, PayKernel kernel, String channelName) {
        this(config, kernel, channelName, false);
    }

    /**
     * 供 V2 / V3 适配器选择对应依赖探测。
     *
     * @param config      配置域
     * @param kernel      内核边界
     * @param channelName 通道标识
     * @param v2          {@code true} 表示支付宝 V2
     */
    DefaultPayClient(PayConfig config, PayKernel kernel, String channelName, boolean v2) {
        if (config == null) {
            throw new IllegalArgumentException("config 不能为 null");
        }
        if (kernel == null) {
            throw new IllegalArgumentException("kernel 不能为 null");
        }
        if (channelName == null || channelName.trim().isEmpty()) {
            throw new IllegalArgumentException("channelName 不能为空");
        }
        this.config = config;
        this.kernel = kernel;
        this.channelName = channelName;
        this.v2 = v2;
    }

    /**
     * 生产形态的工厂方法：用真实内核实现装配。
     *
     * @param config      配置域
     * @param clients     客户端构建与缓存
     * @param channelName 通道标识
     * @return 交易客户端
     */
    public static DefaultPayClient create(PayConfig config, PayClients clients, String channelName) {
        if (clients == null) {
            throw new IllegalArgumentException("clients 不能为 null");
        }
        return new DefaultPayClient(config, new IjpayPayKernel(clients), channelName, false);
    }

    /**
     * 支付宝 V2 生产工厂。
     *
     * @param config      配置域
     * @param clients     V2 客户端缓存
     * @param channelName 通道标识
     * @return 交易客户端
     */
    public static DefaultPayClient createV2(PayConfig config, AliPayV2Clients clients, String channelName) {
        if (clients == null) {
            throw new IllegalArgumentException("clients 不能为 null");
        }
        return new DefaultPayClient(config, new AliPayV2PayKernel(clients), channelName, true);
    }

    private void requireConfiguredKernel() {
        if (v2) {
            PayDeps.requireV2Kernel();
        } else {
            PayDeps.requireV3Kernel();
        }
    }

    @Override
    public PayOrderResult createOrder(String merchantId, String outTradeNo, long amountFen, String subject,
                                      String payType, String returnUrl, String notifyUrl,
                                      Map<String, String> options) {
        String type = payType == null || payType.trim().isEmpty() ? "QRCODE" : payType.trim().toUpperCase();
        if ("QRCODE".equals(type)) {
            return precreate(merchantId, outTradeNo, amountFen, subject, notifyUrl);
        }
        if ("WAP".equals(type)) {
            return wapPay(merchantId, outTradeNo, amountFen, subject, returnUrl, notifyUrl);
        }
        if ("PAGE".equals(type)) {
            return pagePay(merchantId, outTradeNo, amountFen, subject, returnUrl, notifyUrl);
        }
        throw new PayException("支付宝不支持的 payType：" + payType);
    }

    @Override
    public String channelName() {
        return channelName;
    }

    /**
     * 扫码预下单。
     */
    public PayOrderResult precreate(String appId, String outTradeNo, long amountFen, String subject,
                                    String notifyUrl) {
        requireConfiguredKernel();
        requireText(outTradeNo, "outTradeNo", MAX_TRADE_NO_LENGTH);
        PayAmounts.requirePositive(amountFen, "amountFen");
        requireText(subject, "subject", MAX_SUBJECT_LENGTH);
        PayAppConfig app = config.resolve(appId);
        String notify = callback(app.notifyUrl(), notifyUrl, PayConfig.KEY_NOTIFY_URL, "precreate");

        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put(IjpayPayKernel.PARAM_OUT_TRADE_NO, outTradeNo.trim());
        bizParams.put(IjpayPayKernel.PARAM_TOTAL_AMOUNT, PayAmounts.fenToYuan(amountFen));
        bizParams.put(IjpayPayKernel.PARAM_SUBJECT, subject.trim());
        bizParams.put(IjpayPayKernel.PARAM_NOTIFY_URL, notify);
        bizParams.put(IjpayPayKernel.PARAM_PRODUCT_CODE, PRODUCT_CODE_FACE_TO_FACE);

        Object response;
        try {
            response = kernel.precreate(app, bizParams);
        } catch (Exception e) {
            throw PayErrors.kernelFailure("扫码预下单", e);
        }
        return PayResults.toOrderResult(PayOrderResult.Channel.QRCODE, outTradeNo.trim(),
                app.pageHttpMethod(), response);
    }

    /**
     * 手机网站支付。
     */
    public PayOrderResult wapPay(String appId, String outTradeNo, long amountFen, String subject,
                                 String returnUrl, String notifyUrl) {
        return pagePay(appId, outTradeNo, amountFen, subject, returnUrl, notifyUrl,
                PayOrderResult.Channel.WAP, METHOD_WAP_PAY, PRODUCT_CODE_WAP, "手机网站支付");
    }

    /**
     * 电脑网站支付。
     */
    public PayOrderResult pagePay(String appId, String outTradeNo, long amountFen, String subject,
                                  String returnUrl, String notifyUrl) {
        return pagePay(appId, outTradeNo, amountFen, subject, returnUrl, notifyUrl,
                PayOrderResult.Channel.PAGE, METHOD_PAGE_PAY, PRODUCT_CODE_PAGE, "电脑网站支付");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PayQueryResult query(String appId, String outTradeNo) {
        requireConfiguredKernel();
        requireText(outTradeNo, "outTradeNo", MAX_TRADE_NO_LENGTH);
        PayAppConfig app = config.resolve(appId);

        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put(IjpayPayKernel.PARAM_OUT_TRADE_NO, outTradeNo.trim());

        Object response;
        try {
            response = kernel.query(app, bizParams);
        } catch (Exception e) {
            throw PayErrors.kernelFailure("查单", e);
        }
        return PayResults.toQueryResult(outTradeNo.trim(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PayRefundResult refund(String appId, String outTradeNo, String outRequestNo, long refundFen,
                                  String refundReason) {
        requireConfiguredKernel();
        requireText(outTradeNo, "outTradeNo", MAX_TRADE_NO_LENGTH);
        requireText(outRequestNo, "outRequestNo", MAX_TRADE_NO_LENGTH);
        PayAmounts.requirePositive(refundFen, "refundFen");
        PayAppConfig app = config.resolve(appId);

        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put(IjpayPayKernel.PARAM_OUT_TRADE_NO, outTradeNo.trim());
        bizParams.put(IjpayPayKernel.PARAM_OUT_REQUEST_NO, outRequestNo.trim());
        bizParams.put(IjpayPayKernel.PARAM_REFUND_AMOUNT, PayAmounts.fenToYuan(refundFen));
        if (!PayConfig.isBlank(refundReason)) {
            bizParams.put(IjpayPayKernel.PARAM_REFUND_REASON, refundReason.trim());
        }

        Object response;
        try {
            response = kernel.refund(app, bizParams);
        } catch (Exception e) {
            throw PayErrors.kernelFailure("退款", e);
        }
        return PayResults.toRefundResult(outTradeNo.trim(), outRequestNo.trim(), response);
    }

    /**
     * 页面类下单的共用编排。
     *
     * <p>业务参数放在 {@code biz_content} 里、回调地址作为公共参数平铺——这是内核
     * {@code GenericExecuteApi} 的参数约定：{@code biz_content} 会被序列化为 JSON，其余键进入公共参数
     * （`docs/design.md` §2.4.5）。</p>
     *
     * @param appId      应用编号
     * @param outTradeNo 商户订单号
     * @param amountFen  订单金额（分）
     * @param subject    订单标题
     * @param returnUrl  同步跳转地址
     * @param notifyUrl  异步回调地址
     * @param channel    下单渠道
     * @param method     接口方法名
     * @param productCode 产品码
     * @param action     接口语义（异常消息用）
     * @return 下单结果
     */
    private PayOrderResult pagePay(String appId, String outTradeNo, long amountFen, String subject, String returnUrl,
                                   String notifyUrl, PayOrderResult.Channel channel, String method,
                                   String productCode, String action) {
        requireConfiguredKernel();
        requireText(outTradeNo, "outTradeNo", MAX_TRADE_NO_LENGTH);
        PayAmounts.requirePositive(amountFen, "amountFen");
        requireText(subject, "subject", MAX_SUBJECT_LENGTH);
        PayAppConfig app = config.resolve(appId);
        String notify = callback(app.notifyUrl(), notifyUrl, PayConfig.KEY_NOTIFY_URL, action);
        String sync = callback(app.returnUrl(), returnUrl, PayConfig.KEY_RETURN_URL, action);

        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put(IjpayPayKernel.PARAM_OUT_TRADE_NO, outTradeNo.trim());
        bizContent.put(IjpayPayKernel.PARAM_TOTAL_AMOUNT, PayAmounts.fenToYuan(amountFen));
        bizContent.put(IjpayPayKernel.PARAM_SUBJECT, subject.trim());
        bizContent.put(IjpayPayKernel.PARAM_PRODUCT_CODE, productCode);
        bizContent.put(IjpayPayKernel.PARAM_QUIT_URL, sync);

        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put(IjpayPayKernel.PARAM_BIZ_CONTENT, bizContent);
        bizParams.put(IjpayPayKernel.PARAM_RETURN_URL, sync);
        bizParams.put(IjpayPayKernel.PARAM_NOTIFY_URL, notify);

        String payload;
        try {
            payload = kernel.pagePay(app, method, app.pageHttpMethod(), bizParams);
        } catch (Exception e) {
            throw PayErrors.kernelFailure(action, e);
        }
        return PayResults.toOrderResult(channel, outTradeNo.trim(), app.pageHttpMethod(), payload);
    }

    /**
     * 回调地址回退：显式入参优先，其次应用配置里的全局默认值。
     *
     * @param configured 配置值
     * @param explicit   入参
     * @param key        键名（异常消息用）
     * @param action     接口语义（异常消息用）
     * @return 可用的回调地址
     */
    private static String callback(String configured, String explicit, String key, String action) {
        String value = PayConfig.isBlank(explicit) ? configured : explicit.trim();
        if (PayConfig.isBlank(value)) {
            throw PayErrors.missingCallback(action, key);
        }
        return value.trim();
    }

    /**
     * 校验必填文本与长度上限。
     *
     * @param value 文本
     * @param name  参数名
     * @param max   长度上限
     */
    private static void requireText(String value, String name, int max) {
        if (PayConfig.isBlank(value)) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(name + " 长度不能超过 " + max + "，实际为 " + trimmed.length());
        }
    }
}
