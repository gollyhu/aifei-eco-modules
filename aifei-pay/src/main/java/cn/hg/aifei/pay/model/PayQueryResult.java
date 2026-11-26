/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.model;

/**
 * 查单结果值对象：字段为内核原样透出的字符串，金额单位为元。
 *
 * <p>缺失字段一律归一为空串 {@code ""}，不用 {@code null}——便于调用方直接拼接与判空。</p>
 */
public final class PayQueryResult {

    private final String outTradeNo;

    private final String tradeNo;

    private final String tradeStatus;

    private final String totalAmount;

    private final String buyerPayAmount;

    private final String sendPayDate;

    /**
     * 唯一构造入口。
     *
     * @param outTradeNo    商户订单号
     * @param tradeNo       支付宝交易号
     * @param tradeStatus   交易状态
     * @param totalAmount   订单总金额（元）
     * @param buyerPayAmount 买家实付金额（元）
     * @param sendPayDate   支付时间
     */
    private PayQueryResult(String outTradeNo, String tradeNo, String tradeStatus, String totalAmount,
                           String buyerPayAmount, String sendPayDate) {
        this.outTradeNo = outTradeNo;
        this.tradeNo = tradeNo;
        this.tradeStatus = tradeStatus;
        this.totalAmount = totalAmount;
        this.buyerPayAmount = buyerPayAmount;
        this.sendPayDate = sendPayDate;
    }

    /**
     * 工厂方法。
     *
     * @param outTradeNo    商户订单号
     * @param tradeNo       支付宝交易号
     * @param tradeStatus   交易状态
     * @param totalAmount   订单总金额（元）
     * @param buyerPayAmount 买家实付金额（元）
     * @param sendPayDate   支付时间
     * @return 查单结果
     */
    public static PayQueryResult of(String outTradeNo, String tradeNo, String tradeStatus, String totalAmount,
                                    String buyerPayAmount, String sendPayDate) {
        return new PayQueryResult(outTradeNo, tradeNo, tradeStatus, totalAmount, buyerPayAmount, sendPayDate);
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
     * @return 交易状态，如 {@code TRADE_SUCCESS}
     */
    public String tradeStatus() {
        return tradeStatus;
    }

    /**
     * @return 订单总金额（元）
     */
    public String totalAmount() {
        return totalAmount;
    }

    /**
     * @return 买家实付金额（元）
     */
    public String buyerPayAmount() {
        return buyerPayAmount;
    }

    /**
     * @return 支付时间
     */
    public String sendPayDate() {
        return sendPayDate;
    }
}
