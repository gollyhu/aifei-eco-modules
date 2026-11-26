/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import cn.hg.aifei.pay.model.PayOrderResult;
import cn.hg.aifei.pay.model.PayQueryResult;
import cn.hg.aifei.pay.model.PayRefundResult;

/**
 * 支付宝交易动作视图：下单（扫码 / H5 / PC）、查单、退款。
 *
 * <p>由装配期注册为 {@code @Inject} 单例；静态上下文可用 {@link AliPayKit#client()} 取同一实例。</p>
 *
 * <p><b>统一参数语义</b>：{@code appId} 为每个方法的第一参数，{@code null} / 空白时取
 * {@code pay.default-app-id}，仍无则抛 {@link AliPayException}；金额参数单位为分且必须大于 0；
 * 回调地址参数为 {@code null} / 空白时回退到 {@code pay.notify-url} / {@code pay.return-url}。</p>
 *
 * <p><b>异常语义</b>：调用方参数写错抛 {@link IllegalArgumentException}；配置非法、未装配、依赖缺失、
 * 内核失败一律抛 {@link AliPayException}（受检异常不外泄）。</p>
 */
public interface AliPayClient {

    /**
     * 当面付扫码预下单。
     *
     * @param appId      应用编号，可为 {@code null}（回退默认应用）
     * @param outTradeNo 商户订单号，1–64 字符，非空白
     * @param amountFen  订单金额，单位分，必须大于 0
     * @param subject    订单标题，1–256 字符，非空白
     * @param notifyUrl  异步回调地址，可为 {@code null}（回退配置）
     * @return 下单结果，载荷类型恒为 {@code QRCODE}
     * @throws IllegalArgumentException 参数不合法
     * @throws AliPayException          配置非法 / 未装配 / 依赖缺失 / 内核失败
     */
    PayOrderResult precreate(String appId, String outTradeNo, long amountFen, String subject,
                             String notifyUrl);

    /**
     * 手机网站支付（H5）。
     *
     * @param appId      应用编号，可为 {@code null}（回退默认应用）
     * @param outTradeNo 商户订单号，1–64 字符，非空白
     * @param amountFen  订单金额，单位分，必须大于 0
     * @param subject    订单标题，1–256 字符，非空白
     * @param returnUrl  同步跳转地址，可为 {@code null}（回退配置），回退后仍为空则失败
     * @param notifyUrl  异步回调地址，可为 {@code null}（回退配置）
     * @return 下单结果，载荷类型由 {@code pay.page-http-method} 决定（{@code POST} → {@code FORM}）
     * @throws IllegalArgumentException 参数不合法
     * @throws AliPayException          配置非法 / 未装配 / 依赖缺失 / 内核失败
     */
    PayOrderResult wapPay(String appId, String outTradeNo, long amountFen, String subject,
                          String returnUrl, String notifyUrl);

    /**
     * 电脑网站支付（PC）。
     *
     * @param appId      应用编号，可为 {@code null}（回退默认应用）
     * @param outTradeNo 商户订单号，1–64 字符，非空白
     * @param amountFen  订单金额，单位分，必须大于 0
     * @param subject    订单标题，1–256 字符，非空白
     * @param returnUrl  同步跳转地址，可为 {@code null}（回退配置），回退后仍为空则失败
     * @param notifyUrl  异步回调地址，可为 {@code null}（回退配置）
     * @return 下单结果，载荷类型由 {@code pay.page-http-method} 决定（{@code POST} → {@code FORM}）
     * @throws IllegalArgumentException 参数不合法
     * @throws AliPayException          配置非法 / 未装配 / 依赖缺失 / 内核失败
     */
    PayOrderResult pagePay(String appId, String outTradeNo, long amountFen, String subject,
                           String returnUrl, String notifyUrl);

    /**
     * 查单。
     *
     * <p>交易不存在时抛 {@link AliPayException} 且 {@code code()} 为 {@code ACQ.TRADE_NOT_EXIST}，
     * 不返回「空结果」形态——口径唯一。</p>
     *
     * @param appId      应用编号，可为 {@code null}（回退默认应用）
     * @param outTradeNo 商户订单号，1–64 字符，非空白
     * @return 查单结果
     * @throws IllegalArgumentException 参数不合法
     * @throws AliPayException          配置非法 / 未装配 / 依赖缺失 / 内核失败
     */
    PayQueryResult query(String appId, String outTradeNo);

    /**
     * 退款。
     *
     * @param appId        应用编号，可为 {@code null}（回退默认应用）
     * @param outTradeNo   商户订单号，1–64 字符，非空白
     * @param outRequestNo 退款请求号（幂等键），1–64 字符，非空白，原样透传给内核
     * @param refundFen    退款金额，单位分，必须大于 0
     * @param refundReason 退款原因，可为 {@code null} / 空白（不提交该字段）
     * @return 退款结果
     * @throws IllegalArgumentException 参数不合法
     * @throws AliPayException          配置非法 / 未装配 / 依赖缺失 / 内核失败
     */
    PayRefundResult refund(String appId, String outTradeNo, String outRequestNo, long refundFen,
                           String refundReason);
}
