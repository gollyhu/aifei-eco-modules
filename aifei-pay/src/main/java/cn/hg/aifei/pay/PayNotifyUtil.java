/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import cn.hg.aifei.pay.internal.PayChannelRegistry;
import cn.hg.aifei.pay.internal.PayErrors;
import cn.hg.aifei.pay.internal.PayForms;
import cn.hg.aifei.pay.model.PayNotifyRequest;
import cn.hg.aifei.pay.spi.PayNotify;
import java.util.Map;

/**
 * 无状态回调验签入口：不读 HTTP、不写响应、不调监听器。
 */
public final class PayNotifyUtil {

    private static final String PARAM_REFUND_FEE = "refund_fee";
    private static final String PARAM_OUT_REQUEST_NO = "out_request_no";

    private PayNotifyUtil() {
    }

    /**
     * 验签。
     *
     * @param channelName 通道名
     * @param merchantId  商户 / 应用标识，可为空
     * @param request     回调入参
     * @return 通过为 {@code true}
     */
    public static boolean verify(String channelName, String merchantId, PayNotifyRequest request) {
        Map<String, String> params = params(request);
        return PayChannelRegistry.verifier(channelName).verify(merchantId, params);
    }

    /**
     * 默认通道验签（Map 入参）。
     */
    public static boolean verify(String merchantId, Map<String, String> params) {
        return verify(defaultChannel(), merchantId, PayNotifyRequest.of(params));
    }

    /**
     * 默认通道验签（urlencoded 报文入参）。
     */
    public static boolean verify(String merchantId, String formBody) {
        return verify(defaultChannel(), merchantId, PayNotifyRequest.of(null, formBody));
    }

    /**
     * 默认通道验签并解析（Map 入参）。
     */
    public static PayNotify verifyAndParse(String merchantId, Map<String, String> params) {
        return verifyAndParse(defaultChannel(), merchantId, PayNotifyRequest.of(params));
    }

    /**
     * 默认通道验签并解析（urlencoded 报文入参）。
     */
    public static PayNotify verifyAndParse(String merchantId, String formBody) {
        return verifyAndParse(defaultChannel(), merchantId, PayNotifyRequest.of(null, formBody));
    }

    /**
     * 验签并解析为统一回调事件。
     *
     * @param channelName 通道名
     * @param merchantId  商户 / 应用标识，可为空
     * @param request     回调入参
     * @return 验签通过的回调事件
     */
    public static PayNotify verifyAndParse(String channelName, String merchantId, PayNotifyRequest request) {
        Map<String, String> params = params(request);
        if (!PayChannelRegistry.verifier(channelName).verify(merchantId, params)) {
            throw PayErrors.verifyFailed(merchantId, "签名校验不通过");
        }
        Map<String, String> copy = PayForms.copy(params);
        PayNotify.Type type = notifyType(copy) == PayNotify.Type.REFUND
                ? PayNotify.Type.REFUND : PayNotify.Type.TRADE;
        return new PayNotify(channelName, merchantId, type, value(copy, "out_trade_no"),
                value(copy, "trade_no"), value(copy, "out_request_no"),
                value(copy, "trade_status"), value(copy, "total_amount"),
                request == null ? "" : request.body(), copy);
    }

    private static String value(Map<String, String> params, String key) {
        String text = params.get(key);
        return text == null ? "" : text;
    }

    private static String defaultChannel() {
        return PayChannelRegistry.defaultClient().channelName();
    }

    private static Map<String, String> params(PayNotifyRequest request) {
        if (request == null) {
            throw PayErrors.verifyFailed(null, "回调入参为 null");
        }
        Map<String, String> params = request.params();
        if (params != null && !params.isEmpty()) {
            return params;
        }
        if (request.body() == null || request.body().trim().isEmpty()) {
            throw PayErrors.verifyFailed(null, "报文内容与参数均为空");
        }
        return PayForms.parse(request.body());
    }

    private static PayNotify.Type notifyType(Map<String, String> params) {
        if (!isEmpty(params.get(PARAM_REFUND_FEE)) || !isEmpty(params.get(PARAM_OUT_REQUEST_NO))) {
            return PayNotify.Type.REFUND;
        }
        return PayNotify.Type.TRADE;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
