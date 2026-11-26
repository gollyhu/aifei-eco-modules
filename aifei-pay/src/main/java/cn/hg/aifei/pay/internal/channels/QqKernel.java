/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import com.ijpay.core.enums.SignType;
import com.ijpay.core.kit.WxPayKit;
import com.ijpay.qqpay.QqPayApi;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * QQ 钱包内核：显式参数调用 {@code QqPayApi}，不触碰 {@code QqPayApiConfigKit}。
 */
public final class QqKernel implements ChannelKernel {

    @Override
    public Object precreate(ChannelConfig config, Map<String, String> params) {
        return ChannelResponses.xml(QqPayApi.unifiedOrder(request(config, params, "NATIVE")));
    }

    @Override
    public String pagePay(ChannelConfig config, String payType, Map<String, String> params) {
        String tradeType = "PAGE".equals(payType) ? "QT" : "WAP";
        Map<String, String> response = ChannelResponses.xml(QqPayApi.unifiedOrder(request(config, params, tradeType)));
        String url = response.get("mweb_url");
        return url == null || url.isEmpty() ? ChannelResponses.text(response, "code_url") : url;
    }

    @Override
    public Object query(ChannelConfig config, Map<String, String> params) {
        return ChannelResponses.xml(QqPayApi.orderQuery(request(config, params, null)));
    }

    @Override
    public Object refund(ChannelConfig config, Map<String, String> params) {
        Map<String, String> request = request(config, params, null);
        request.put("out_refund_no", params.get(PARAM_OUT_REQUEST_NO));
        request.put("refund_fee", params.get(PARAM_REFUND_FEN));
        String totalFee = params.get("total_fee");
        request.put("total_fee", totalFee == null ? params.get(PARAM_REFUND_FEN) : totalFee);
        request.put("sign", WxPayKit.createSign(request, config.require("partner-key"), SignType.MD5));
        return ChannelResponses.xml(QqPayApi.orderRefund(request, config.require("cert-path"),
                config.value("cert-password", "")));
    }

    public static boolean verify(ChannelConfig config, Map<String, String> params) {
        try {
            return WxPayKit.verifyNotify(new LinkedHashMap<String, String>(params),
                    config.require("partner-key"), SignType.MD5);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Map<String, String> request(ChannelConfig config, Map<String, String> params, String tradeType) {
        Map<String, String> request = new LinkedHashMap<String, String>(params);
        for (String key : new String[]{"merchant_id", "amount_fen", "subject", "pay_type", "out_request_no",
                "refund_fen", "refund_reason", "currency", "sign", "total_fee"}) {
            request.remove(key);
        }
        request.put("appid", config.require("app-id"));
        request.put("mch_id", params.get("merchant_id"));
        request.put("nonce_str", WxPayKit.generateStr());
        request.put("fee_type", "CNY");
        request.put("body", params.get(PARAM_SUBJECT));
        request.put("out_trade_no", params.get(PARAM_OUT_TRADE_NO));
        request.put("total_fee", params.get(PARAM_AMOUNT_FEN));
        request.put("notify_url", params.get(PARAM_NOTIFY_URL));
        if (tradeType != null) {
            request.put("trade_type", tradeType);
        }
        request.put("sign", WxPayKit.createSign(request, config.require("partner-key"), SignType.MD5));
        return request;
    }
}
