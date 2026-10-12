/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay;

import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.pay.core.PayHttpResponse;
import cn.aifei.weixin.pay.core.PaySignature;
import com.alibaba.fastjson2.JSONObject;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信支付 V3 接口：JSAPI 下单 / 查单 / 关单 / 退款。
 *
 * <p>错误契约：非 2xx 抛 {@link WxApiException}（携带官方 code / message）；
 * 2xx 响应原样返回 JSONObject 由调用方判别。全部方法线程安全，签名参数即时生成。
 */
public class PayApi {

    public static final String API_BASE = "https://api.mch.weixin.qq.com/";

    private static final String JSAPI_PATH = "v3/pay/transactions/jsapi";
    private static final String REFUND_PATH = "v3/refund/domestic/refunds";

    /**
     * JSAPI / 小程序下单。
     *
     * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/apis/jsapi-payment/direct-jsons/jsapi-prepay.html
     *
     * @return 响应 JSON，含 prepay_id
     */
    public static JSONObject jsapi(PayMerchant merchant, String appId, String description,
                                   String outTradeNo, String notifyUrl, int totalFen, String openid) {
        JSONObject amount = new JSONObject();
        amount.put("total", totalFen);
        amount.put("currency", "CNY");
        JSONObject payer = new JSONObject();
        payer.put("openid", openid);

        JSONObject body = new JSONObject();
        body.put("appid", appId);
        body.put("mchid", merchant.getMchId());
        body.put("description", description);
        body.put("out_trade_no", outTradeNo);
        body.put("notify_url", notifyUrl);
        body.put("amount", amount);
        body.put("payer", payer);
        return execute(merchant, "POST", JSAPI_PATH, body.toJSONString());
    }

    /**
     * 查询订单（商户单号）。
     *
     * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/apis/jsapi-payment/query-by-out-trade-no.html
     */
    public static JSONObject queryByOutTradeNo(PayMerchant merchant, String outTradeNo) {
        String path = "v3/pay/transactions/out-trade-no/" + outTradeNo + "?mchid=" + merchant.getMchId();
        return execute(merchant, "GET", path, "");
    }

    /**
     * 关闭订单（HTTP 204 无响应体）。
     *
     * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/apis/jsapi-payment/close-order.html
     */
    public static void close(PayMerchant merchant, String outTradeNo) {
        JSONObject body = new JSONObject();
        body.put("mchid", merchant.getMchId());
        execute(merchant, "POST", "v3/pay/transactions/out-trade-no/" + outTradeNo + "/close",
                body.toJSONString());
    }

    /**
     * 申请退款。
     *
     * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/apis/refund-v3/refund/apply-refund.html
     *
     * @param outRefundNo 商户退款单号（幂等源）
     * @param totalFen    原订单金额（分，官方要求与 refund 一同提交）
     */
    public static JSONObject refund(PayMerchant merchant, String outTradeNo, String outRefundNo,
                                    int refundFen, int totalFen, String notifyUrl) {
        JSONObject amount = new JSONObject();
        amount.put("refund", refundFen);
        amount.put("total", totalFen);
        amount.put("currency", "CNY");

        JSONObject body = new JSONObject();
        body.put("out_trade_no", outTradeNo);
        body.put("out_refund_no", outRefundNo);
        body.put("amount", amount);
        if (notifyUrl != null && !notifyUrl.isEmpty()) {
            body.put("notify_url", notifyUrl);
        }
        return execute(merchant, "POST", REFUND_PATH, body.toJSONString());
    }

    /**
     * 前端拉起支付参数（prepay_id 二次签名），见 {@link PaySignature#buildJsapiPayParams}
     */
    public static PaySignature.JsapiPayParams buildJsapiPayParams(String appId, PayMerchant merchant,
                                                                  String prepayId) {
        return PaySignature.buildJsapiPayParams(appId, merchant, prepayId);
    }

    private static JSONObject execute(PayMerchant merchant, String method, String pathWithQuery, String body) {
        // V3 签名 message 中的 canonical URL 必须带前导 /（pathWithQuery 以 "v3/" 起始）
        String canonicalUrl = "/" + pathWithQuery;
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", PaySignature.buildAuthorization(merchant, method, canonicalUrl, body));
        headers.put("Accept", "application/json");
        if ("POST".equals(method)) {
            headers.put("Content-Type", "application/json");
        }

        PayHttpResponse response = "GET".equals(method)
                ? WxPayConfig.http().get(API_BASE + pathWithQuery, headers)
                : WxPayConfig.http().post(API_BASE + pathWithQuery, headers, body);

        if (!response.is2xx()) {
            throw new WxApiException("微信支付接口失败 [HTTP " + response.getStatusCode() + "] " + errorDetail(response));
        }
        String responseBody = response.getBody();
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return new JSONObject();
        }
        return JSONObject.parseObject(responseBody);
    }

    /**
     * V3 错误响应体为 {"code":"...","message":"..."}；非 JSON 时降级为原文截断
     */
    private static String errorDetail(PayHttpResponse response) {
        try {
            JSONObject error = JSONObject.parseObject(response.getBody());
            if (error != null && error.getString("code") != null) {
                return "code=" + error.getString("code") + " message=" + error.getString("message");
            }
        } catch (RuntimeException ignore) {
        }
        String body = response.getBody();
        return body == null ? "" : (body.length() > 200 ? body.substring(0, 200) + "..." : body);
    }

    private PayApi() {
    }
}
