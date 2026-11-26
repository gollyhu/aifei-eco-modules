/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.model;

/**
 * 退款结果值对象：金额字段为内核原样透出的元字符串。
 *
 * <p>{@link #outRequestNo()} 是退款幂等键的回显——模块不去重、不改写，透传给内核的值与传入值一致。</p>
 */
public final class PayRefundResult {

    private final String outTradeNo;

    private final String tradeNo;

    private final String outRequestNo;

    private final String refundFee;

    private final String fundChange;

    private final String gmtRefundPay;

    /**
     * 唯一构造入口。
     *
     * @param outTradeNo   商户订单号
     * @param tradeNo      支付宝交易号
     * @param outRequestNo 退款请求号（幂等键回显）
     * @param refundFee    本次退款金额（元）
     * @param fundChange   退款是否发生资金变化
     * @param gmtRefundPay 退款时间
     */
    private PayRefundResult(String outTradeNo, String tradeNo, String outRequestNo, String refundFee,
                            String fundChange, String gmtRefundPay) {
        this.outTradeNo = outTradeNo;
        this.tradeNo = tradeNo;
        this.outRequestNo = outRequestNo;
        this.refundFee = refundFee;
        this.fundChange = fundChange;
        this.gmtRefundPay = gmtRefundPay;
    }

    /**
     * 工厂方法。
     *
     * @param outTradeNo   商户订单号
     * @param tradeNo      支付宝交易号
     * @param outRequestNo 退款请求号（幂等键回显）
     * @param refundFee    本次退款金额（元）
     * @param fundChange   退款是否发生资金变化
     * @param gmtRefundPay 退款时间
     * @return 退款结果
     */
    public static PayRefundResult of(String outTradeNo, String tradeNo, String outRequestNo, String refundFee,
                                     String fundChange, String gmtRefundPay) {
        return new PayRefundResult(outTradeNo, tradeNo, outRequestNo, refundFee, fundChange, gmtRefundPay);
    }

    /**
     * @return 商户订单号
     */
    public String outTradeNo() {
        return outTradeNo;
    }

    /**
     * @return 支付宝交易号
     */
    public String tradeNo() {
        return tradeNo;
    }

    /**
     * @return 退款请求号（幂等键回显）
     */
    public String outRequestNo() {
        return outRequestNo;
    }

    /**
     * @return 本次退款金额（元）
     */
    public String refundFee() {
        return refundFee;
    }

    /**
     * @return 退款是否发生资金变化
     */
    public String fundChange() {
        return fundChange;
    }

    /**
     * @return 退款时间
     */
    public String gmtRefundPay() {
        return gmtRefundPay;
    }
}
