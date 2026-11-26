/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import cn.hg.aifei.pay.model.PayOrderResult;
import cn.hg.aifei.pay.model.PayQueryResult;
import cn.hg.aifei.pay.model.PayRefundResult;
import java.util.Map;

/**
 * 支付通道客户端：每个实例绑定一个通道，动作语义跨通道一致。
 */
public interface PayClient {

    /**
     * 下单。
     *
     * @param merchantId 通道内商户 / 应用标识，可为空回退通道默认值
     * @param outTradeNo 商户订单号
     * @param amountFen  金额，单位分，必须大于 0
     * @param subject    订单标题
     * @param payType    通道内产品类型，如支付宝 {@code QRCODE} / {@code WAP} / {@code PAGE}
     * @param returnUrl  同步跳转地址，可为空
     * @param notifyUrl  异步回调地址，可为空
     * @param options    通道扩展参数，可为空
     * @return 下单结果
     */
    PayOrderResult createOrder(String merchantId, String outTradeNo, long amountFen, String subject,
                               String payType, String returnUrl, String notifyUrl,
                               Map<String, String> options);

    /**
     * 查单。
     *
     * @param merchantId 商户 / 应用标识，可为空
     * @param outTradeNo 商户订单号
     * @return 查单结果
     */
    PayQueryResult query(String merchantId, String outTradeNo);

    /**
     * 退款。
     *
     * @param merchantId   商户 / 应用标识，可为空
     * @param outTradeNo   商户订单号
     * @param outRequestNo 退款请求号（幂等键）
     * @param refundFen    退款金额，单位分
     * @param refundReason 退款原因，可为空
     * @return 退款结果
     */
    PayRefundResult refund(String merchantId, String outTradeNo, String outRequestNo, long refundFen,
                           String refundReason);

    /**
     * @return 实例绑定的通道标识
     */
    String channelName();
}
