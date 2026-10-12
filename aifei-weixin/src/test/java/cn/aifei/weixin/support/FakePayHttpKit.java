/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.support;

import cn.aifei.weixin.pay.PayHttpKit;
import cn.aifei.weixin.pay.core.PayHttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单测用伪支付 HTTP 客户端：记录请求、按需返回，全程不联网
 */
public class FakePayHttpKit implements PayHttpKit {

    public final List<String> getUrls = new ArrayList<>();
    public final List<String> postUrls = new ArrayList<>();
    public final List<String> postBodies = new ArrayList<>();
    public final List<Map<String, String>> getHeaders = new ArrayList<>();
    public final List<Map<String, String>> postHeaders = new ArrayList<>();

    public PayHttpResponse getResponse = new PayHttpResponse(200, new HashMap<String, String>(), "{}");
    public PayHttpResponse postResponse = new PayHttpResponse(200, new HashMap<String, String>(), "{}");

    @Override
    public PayHttpResponse get(String url, Map<String, String> headers) {
        getUrls.add(url);
        getHeaders.add(headers);
        return getResponse;
    }

    @Override
    public PayHttpResponse post(String url, Map<String, String> headers, String jsonBody) {
        postUrls.add(url);
        postHeaders.add(headers);
        postBodies.add(jsonBody);
        return postResponse;
    }
}
