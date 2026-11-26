/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 验签通过后的统一回调事件。
 */
public final class PayNotify {

    public enum Type { TRADE, REFUND }

    private final String channelName;
    private final String merchantId;
    private final Type type;
    private final String outTradeNo;
    private final String tradeNo;
    private final String outRequestNo;
    private final String tradeStatus;
    private final String totalAmount;
    private final String rawBody;
    private final Map<String, String> params;

    public PayNotify(String channelName, String merchantId, Type type, String outTradeNo, String tradeNo,
                     String outRequestNo, String tradeStatus, String totalAmount, String rawBody,
                     Map<String, String> params) {
        this.channelName = channelName;
        this.merchantId = merchantId;
        this.type = type;
        this.outTradeNo = value(outTradeNo);
        this.tradeNo = value(tradeNo);
        this.outRequestNo = value(outRequestNo);
        this.tradeStatus = value(tradeStatus);
        this.totalAmount = value(totalAmount);
        this.rawBody = value(rawBody);
        Map<String, String> source = params == null ? Collections.<String, String>emptyMap() : params;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<String, String>(source));
    }

    public String channelName() {
        return channelName;
    }

    public String merchantId() {
        return merchantId;
    }

    public Type type() {
        return type;
    }

    public String outTradeNo() {
        return outTradeNo;
    }

    public String tradeNo() {
        return tradeNo;
    }

    public String outRequestNo() {
        return outRequestNo;
    }

    public String tradeStatus() {
        return tradeStatus;
    }

    public String totalAmount() {
        return totalAmount;
    }

    public String refundFee() {
        return value(params.get("refund_fee"));
    }

    public String appId() {
        return value(params.get("app_id"));
    }

    public String rawBody() {
        return rawBody;
    }

    public Map<String, String> params() {
        return params;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
