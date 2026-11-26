/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import com.ijpay.core.enums.SignType;
import com.ijpay.core.kit.WxPayKit;
import com.ijpay.xpay.PayApi;
import com.ijpay.xpay.PayUrl;
import com.ijpay.xpay.enums.AliPayUrlEnum;
import com.ijpay.xpay.enums.WxPayUrlEnum;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XPay 内核：显式网关与密钥调用 {@code PayApi.exePost}，接口由渠道枚举选择。
 */
public final class XPayKernel implements ChannelKernel {

    @Override
    public Object precreate(ChannelConfig config, Map<String, String> params) {
        PayUrl url = "alipay".equalsIgnoreCase(config.value("api-type", "wx"))
                ? AliPayUrlEnum.ALI_NATIVE_PAY : WxPayUrlEnum.NATIVE_PAY;
        return ChannelResponses.body(PayApi.exePost(config.require("server-url"), url, request(config, params)));
    }

    @Override
    public String pagePay(ChannelConfig config, String payType, Map<String, String> params) {
        PayUrl url = "alipay".equalsIgnoreCase(config.value("api-type", "wx"))
                ? AliPayUrlEnum.ALI_WAP_PAY : WxPayUrlEnum.WAP_PAY;
        Map<String, String> response = ChannelResponses.body(PayApi.exePost(config.require("server-url"), url,
                request(config, params)));
        String result = ChannelResponses.text(response, "url");
        return result.isEmpty() ? ChannelResponses.text(response, "code_url") : result;
    }

    @Override
    public Object query(ChannelConfig config, Map<String, String> params) {
        return ChannelResponses.body(PayApi.exePost(config.require("server-url"),
                WxPayUrlEnum.GET_ORDER_INFO, request(config, params)));
    }

    @Override
    public Object refund(ChannelConfig config, Map<String, String> params) {
        PayUrl url = "alipay".equalsIgnoreCase(config.value("api-type", "wx"))
                ? AliPayUrlEnum.ALI_PAY_REFUND_ORDER : WxPayUrlEnum.REFUND_ORDER;
        Map<String, String> request = request(config, params);
        request.put("refund_no", params.get(PARAM_OUT_REQUEST_NO));
        request.put("money", params.get(PARAM_REFUND_FEN));
        request.put("sign", WxPayKit.createSign(request, config.require("api-key"), SignType.MD5));
        return ChannelResponses.body(PayApi.exePost(config.require("server-url"), url, request));
    }

    public static boolean verify(ChannelConfig config, Map<String, String> params) {
        try {
            return WxPayKit.verifyNotify(new LinkedHashMap<String, String>(params),
                    config.require("api-key"), SignType.MD5);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Map<String, String> request(ChannelConfig config, Map<String, String> params) {
        Map<String, String> request = new LinkedHashMap<String, String>();
        request.put("app_id", config.require("app-id"));
        request.put("mch_id", params.get("merchant_id"));
        request.put("out_trade_no", params.get(PARAM_OUT_TRADE_NO));
        request.put("total_fee", params.get(PARAM_AMOUNT_FEN));
        request.put("body", params.get(PARAM_SUBJECT));
        request.put("notify_url", params.get(PARAM_NOTIFY_URL));
        request.put("sign", WxPayKit.createSign(request, config.require("api-key"), SignType.MD5));
        return request;
    }
}
