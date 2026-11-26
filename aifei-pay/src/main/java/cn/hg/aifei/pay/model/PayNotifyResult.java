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
 * 回调验签结果值对象：验签通过后的报文参数 + 业务最常读的幂等键。
 *
 * <p>模块只透出幂等键、不做去重、不做状态机——业务必须自行按 {@link #outTradeNo()}（交易）与
 * {@link #outRequestNo()}（退款）做前置校验。</p>
 */
public final class PayNotifyResult {

    /**
     * 回调类型。
     */
    public enum NotifyType {

        /**
         * 交易类通知。
         */
        TRADE,

        /**
         * 退款类通知。
         */
        REFUND
    }

    private final NotifyType type;

    private final Map<String, String> params;

    /**
     * 唯一构造入口：入参先做不可变拷贝，之后修改原 Map 不影响本对象。
     *
     * @param type   回调类型
     * @param params 报文参数
     */
    private PayNotifyResult(NotifyType type, Map<String, String> params) {
        this.type = type;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    /**
     * 工厂方法。
     *
     * @param type   回调类型
     * @param params 报文参数
     * @return 回调验签结果
     */
    public static PayNotifyResult of(NotifyType type, Map<String, String> params) {
        return new PayNotifyResult(type, params);
    }

    /**
     * @return 回调类型
     */
    public NotifyType type() {
        return type;
    }

    /**
     * @return 报文参数的只读不可变拷贝，供业务读取模块未显式透出的字段
     */
    public Map<String, String> params() {
        return params;
    }

    /**
     * @return {@code params.get("app_id")}，未取到为空串
     */
    public String appId() {
        return value("app_id");
    }

    /**
     * @return {@code params.get("out_trade_no")}（交易幂等键），未取到为空串
     */
    public String outTradeNo() {
        return value("out_trade_no");
    }

    /**
     * @return {@code params.get("trade_no")}，未取到为空串
     */
    public String tradeNo() {
        return value("trade_no");
    }

    /**
     * @return {@code params.get("out_request_no")}（退款幂等键），未取到为空串
     */
    public String outRequestNo() {
        return value("out_request_no");
    }

    /**
     * @return {@code params.get("trade_status")}，未取到为空串
     */
    public String tradeStatus() {
        return value("trade_status");
    }

    /**
     * @return {@code params.get("total_amount")}（元），未取到为空串
     */
    public String totalAmount() {
        return value("total_amount");
    }

    /**
     * @return {@code params.get("refund_fee")}（元），未取到为空串
     */
    public String refundFee() {
        return value("refund_fee");
    }

    /**
     * 取报文参数，缺失归一为空串。
     *
     * @param key 报文键名
     * @return 值，永不为 {@code null}
     */
    private String value(String key) {
        String value = params.get(key);
        return value == null ? "" : value;
    }
}
