/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import com.ijpay.core.enums.SignType;
import com.ijpay.core.kit.WxPayKit;
import com.ijpay.unionpay.UnionPayApi;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 银联内核：显式网关地址 + MD5 签名，调用 {@code UnionPayApi.execution}。
 */
public final class UnionPayKernel implements ChannelKernel {

    @Override
    public Object precreate(ChannelConfig config, Map<String, String> params) {
        Map<String, String> request = request(config, params, config.value("service", "unified.order"));
        request.put("body", params.get(PARAM_SUBJECT));
        request.put("total_fee", params.get(PARAM_AMOUNT_FEN));
        request.put("notify_url", params.get(PARAM_NOTIFY_URL));
        return ChannelResponses.xml(UnionPayApi.execution(config.require("gateway-url"), sign(request, config)));
    }

    @Override
    public String pagePay(ChannelConfig config, String payType, Map<String, String> params) {
        Map<String, String> response = ChannelResponses.xml(UnionPayApi.execution(config.require("gateway-url"),
                sign(request(config, params, config.value("h5-service", "unified.order")), config)));
        String url = response.get("mweb_url");
        return url == null || url.isEmpty() ? ChannelResponses.text(response, "qr_code") : url;
    }

    @Override
    public Object query(ChannelConfig config, Map<String, String> params) {
        Map<String, String> request = request(config, params, config.value("query-service", "unified.query"));
        return ChannelResponses.xml(UnionPayApi.execution(config.require("gateway-url"), sign(request, config)));
    }

    @Override
    public Object refund(ChannelConfig config, Map<String, String> params) {
        Map<String, String> request = request(config, params, config.value("refund-service", "unified.refund"));
        request.put("out_refund_no", params.get(PARAM_OUT_REQUEST_NO));
        request.put("refund_fee", params.get(PARAM_REFUND_FEN));
        return ChannelResponses.xml(UnionPayApi.execution(config.require("gateway-url"), sign(request, config)));
    }

    public static boolean verify(ChannelConfig config, Map<String, String> params) {
        try {
            return WxPayKit.verifyNotify(new LinkedHashMap<String, String>(params),
                    config.require("api-key"), SignType.MD5);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Map<String, String> request(ChannelConfig config, Map<String, String> params, String service) {
        Map<String, String> request = new LinkedHashMap<String, String>();
        request.put("service", service);
        request.put("version", config.value("version", "1.0"));
        request.put("charset", "UTF-8");
        request.put("sign_type", "MD5");
        request.put("mch_id", params.get("merchant_id"));
        request.put("out_trade_no", params.get(PARAM_OUT_TRADE_NO));
        request.put("nonce_str", WxPayKit.generateStr());
        return request;
    }

    private static Map<String, String> sign(Map<String, String> request, ChannelConfig config) {
        request.remove("sign");
        request.put("sign", WxPayKit.createSign(request, config.require("api-key"), SignType.MD5));
        return request;
    }
}
