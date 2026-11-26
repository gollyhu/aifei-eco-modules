/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import java.util.Map;

/**
 * 非支付宝通道的内核边界：把下单 / 查单 / 退款抽象为包级接口，返回统一 Map 响应。
 *
 * <p>与 {@code internal.PayKernel} 同构；返回值刻意用 {@code Object}（Map 或字符串），
 * 由 {@code internal.PayResults} 统一转换，第三方模型不外泄到 {@code ChannelPayClient}。</p>
 */
interface ChannelKernel {

    String PARAM_OUT_TRADE_NO = "out_trade_no";
    String PARAM_AMOUNT_FEN = "amount_fen";
    String PARAM_SUBJECT = "subject";
    String PARAM_NOTIFY_URL = "notify_url";
    String PARAM_RETURN_URL = "return_url";
    String PARAM_PAY_TYPE = "pay_type";
    String PARAM_OUT_REQUEST_NO = "out_request_no";
    String PARAM_REFUND_FEN = "refund_fen";
    String PARAM_REFUND_REASON = "refund_reason";
    String PARAM_CURRENCY = "currency";

    /**
     * 扫码 / 原生下单。
     */
    Object precreate(ChannelConfig config, Map<String, String> params) throws Exception;

    /**
     * 页面类下单，返回跳转 URL 或表单载荷。
     */
    String pagePay(ChannelConfig config, String payType, Map<String, String> params) throws Exception;

    /**
     * 查单。
     */
    Object query(ChannelConfig config, Map<String, String> params) throws Exception;

    /**
     * 退款。
     */
    Object refund(ChannelConfig config, Map<String, String> params) throws Exception;
}
