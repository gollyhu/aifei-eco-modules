/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay;

import cn.aifei.weixin.pay.core.PayHttpResponse;
import java.util.Map;

/**
 * 支付 V3 HTTP 客户端抽象（独立于公众号 {@code HttpKit}：V3 需带自定义 Header 与读取响应头验签）。
 *
 * <p>契约：仅传输失败（网络 / IO）抛 {@code WxApiException}；非 2xx 照常返回响应体，
 * 由调用方判别（V3 错误随非 2xx + JSON body 下发）。
 */
public interface PayHttpKit {

    PayHttpResponse get(String url, Map<String, String> headers);

    PayHttpResponse post(String url, Map<String, String> headers, String jsonBody);
}
