/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多渠道回调入参：统一表达参数、请求头与原始报文体。
 */
public final class PayNotifyRequest {

    private final Map<String, String> params;
    private final Map<String, String> headers;
    private final String body;

    private PayNotifyRequest(Map<String, String> params, Map<String, String> headers, String body) {
        this.params = copy(params);
        this.headers = copy(headers);
        this.body = body;
    }

    public static PayNotifyRequest of(Map<String, String> params) {
        return new PayNotifyRequest(params, null, null);
    }

    public static PayNotifyRequest of(Map<String, String> headers, String body) {
        return new PayNotifyRequest(null, headers, body);
    }

    public static PayNotifyRequest of(Map<String, String> params, Map<String, String> headers, String body) {
        return new PayNotifyRequest(params, headers, body);
    }

    public Map<String, String> params() {
        return params;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String body() {
        return body;
    }

    private static Map<String, String> copy(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(source));
    }
}
