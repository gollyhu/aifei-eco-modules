/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay.core;

import java.util.Locale;
import java.util.Map;

/**
 * 支付接口 HTTP 响应：状态码 / 响应头 / 响应体。非 2xx 不在传输层抛错
 * （V3 错误信息随非 2xx + JSON body 下发，由调用方判别）。
 */
public class PayHttpResponse {

    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;

    public PayHttpResponse(int statusCode, Map<String, String> headers, String body) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
    }

    public boolean is2xx() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * 忽略大小写取响应头（Wechatpay-Timestamp / Wechatpay-Serial 等官方头名固定，但代理层可能改写大小写）
     */
    public String header(String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }
        return null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}
