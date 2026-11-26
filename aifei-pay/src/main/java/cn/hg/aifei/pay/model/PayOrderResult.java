/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.model;

/**
 * 下单结果值对象：把三类下单渠道（扫码 / H5 / PC）的载荷收敛为同一形态。
 *
 * <p>消费方先看 {@link #payloadType()} 再决定载荷用法：{@code QRCODE} 交给二维码渲染、
 * {@code URL} 直接跳转、{@code FORM} 输出到页面由浏览器自提交。</p>
 */
public final class PayOrderResult {

    /**
     * 下单渠道。
     */
    public enum Channel {

        /**
         * 当面付扫码预下单。
         */
        QRCODE,

        /**
         * 手机网站支付（H5）。
         */
        WAP,

        /**
         * 电脑网站支付（PC）。
         */
        PAGE
    }

    /**
     * 载荷类型。
     */
    public enum PayloadType {

        /**
         * 二维码内容串。
         */
        QRCODE,

        /**
         * 可跳转 URL。
         */
        URL,

        /**
         * 自提交表单 HTML。
         */
        FORM
    }

    private final Channel channel;

    private final String outTradeNo;

    private final String tradeNo;

    private final PayloadType payloadType;

    private final String payload;

    /**
     * 唯一构造入口。
     *
     * @param channel     下单渠道
     * @param outTradeNo  商户订单号（回显）
     * @param tradeNo     支付宝交易号，可为 {@code null}
     * @param payloadType 载荷类型
     * @param payload     载荷本体
     */
    private PayOrderResult(Channel channel, String outTradeNo, String tradeNo, PayloadType payloadType,
                           String payload) {
        this.channel = channel;
        this.outTradeNo = outTradeNo;
        this.tradeNo = tradeNo;
        this.payloadType = payloadType;
        this.payload = payload;
    }

    /**
     * 工厂方法。
     *
     * @param channel     下单渠道
     * @param outTradeNo  商户订单号（回显）
     * @param tradeNo     支付宝交易号，可为 {@code null}
     * @param payloadType 载荷类型
     * @param payload     载荷本体
     * @return 下单结果
     */
    public static PayOrderResult of(Channel channel, String outTradeNo, String tradeNo, PayloadType payloadType,
                                    String payload) {
        return new PayOrderResult(channel, outTradeNo, tradeNo, payloadType, payload);
    }

    /**
     * @return 下单渠道
     */
    public Channel channel() {
        return channel;
    }

    /**
     * @return 商户订单号（回显）
     */
    public String outTradeNo() {
        return outTradeNo;
    }

    /**
     * @return 支付宝交易号；扫码预下单阶段通常为空串
     */
    public String tradeNo() {
        return tradeNo;
    }

    /**
     * @return 载荷类型
     */
    public PayloadType payloadType() {
        return payloadType;
    }

    /**
     * @return 载荷本体
     */
    public String payload() {
        return payload;
    }
}
