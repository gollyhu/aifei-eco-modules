/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.ijpay.alipay.AliPayApi;
import java.util.Map;

/**
 * 支付宝 V2 内核边界：IJPay-AliPay + alipay-sdk-java 4.x 的唯一调用点。
 */
final class AliPayV2PayKernel implements PayKernel {

    private final AliPayV2Clients clients;

    AliPayV2PayKernel(AliPayV2Clients clients) {
        if (clients == null) {
            throw new IllegalArgumentException("clients 不能为 null");
        }
        this.clients = clients;
    }

    @Override
    public Object precreate(PayAppConfig app, Map<String, Object> bizParams) throws Exception {
        PayDeps.requireV2Kernel();
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(text(bizParams, IjpayPayKernel.PARAM_OUT_TRADE_NO));
        model.setTotalAmount(text(bizParams, IjpayPayKernel.PARAM_TOTAL_AMOUNT));
        model.setSubject(text(bizParams, IjpayPayKernel.PARAM_SUBJECT));
        model.setProductCode(text(bizParams, IjpayPayKernel.PARAM_PRODUCT_CODE));
        AlipayClient client = clients.client(app.appId());
        return AliPayApi.tradePrecreatePayToResponse(client, Boolean.TRUE, model,
                text(bizParams, IjpayPayKernel.PARAM_NOTIFY_URL));
    }

    @Override
    public String pagePay(PayAppConfig app, String method, String httpMethod, Map<String, Object> bizParams)
            throws Exception {
        PayDeps.requireV2Kernel();
        Map<String, Object> content = content(bizParams);
        AlipayClient client = clients.client(app.appId());
        String returnUrl = text(bizParams, IjpayPayKernel.PARAM_RETURN_URL);
        String notifyUrl = text(bizParams, IjpayPayKernel.PARAM_NOTIFY_URL);
        if ("alipay.trade.wap.pay".equals(method)) {
            AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
            fillPage(model, content);
            return AliPayApi.wapPayStr(client, model, returnUrl, notifyUrl);
        }
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        fillPage(model, content);
        return AliPayApi.tradePage(client, model, returnUrl, notifyUrl);
    }

    @Override
    public Object query(PayAppConfig app, Map<String, Object> bizParams) throws Exception {
        PayDeps.requireV2Kernel();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(text(bizParams, IjpayPayKernel.PARAM_OUT_TRADE_NO));
        return AliPayApi.tradeQueryToResponse(clients.client(app.appId()), Boolean.TRUE, model);
    }

    @Override
    public Object refund(PayAppConfig app, Map<String, Object> bizParams) throws Exception {
        PayDeps.requireV2Kernel();
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(text(bizParams, IjpayPayKernel.PARAM_OUT_TRADE_NO));
        model.setOutRequestNo(text(bizParams, IjpayPayKernel.PARAM_OUT_REQUEST_NO));
        model.setRefundAmount(text(bizParams, IjpayPayKernel.PARAM_REFUND_AMOUNT));
        model.setRefundReason(text(bizParams, IjpayPayKernel.PARAM_REFUND_REASON));
        return AliPayApi.tradeRefundToResponse(clients.client(app.appId()), Boolean.TRUE, model);
    }

    private static void fillPage(AlipayTradeWapPayModel model, Map<String, Object> content) {
        model.setOutTradeNo(text(content, IjpayPayKernel.PARAM_OUT_TRADE_NO));
        model.setTotalAmount(text(content, IjpayPayKernel.PARAM_TOTAL_AMOUNT));
        model.setSubject(text(content, IjpayPayKernel.PARAM_SUBJECT));
        model.setProductCode(text(content, IjpayPayKernel.PARAM_PRODUCT_CODE));
        model.setQuitUrl(text(content, IjpayPayKernel.PARAM_QUIT_URL));
    }

    private static void fillPage(AlipayTradePagePayModel model, Map<String, Object> content) {
        model.setOutTradeNo(text(content, IjpayPayKernel.PARAM_OUT_TRADE_NO));
        model.setTotalAmount(text(content, IjpayPayKernel.PARAM_TOTAL_AMOUNT));
        model.setSubject(text(content, IjpayPayKernel.PARAM_SUBJECT));
        model.setProductCode(text(content, IjpayPayKernel.PARAM_PRODUCT_CODE));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> content(Map<String, Object> bizParams) {
        Object value = bizParams.get(IjpayPayKernel.PARAM_BIZ_CONTENT);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("biz_content 缺失");
        }
        return (Map<String, Object>) value;
    }

    private static String text(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
