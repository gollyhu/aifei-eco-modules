/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import cn.hg.aifei.pay.PayClient;
import cn.hg.aifei.pay.PayException;
import cn.hg.aifei.pay.model.PayOrderResult;
import cn.hg.aifei.pay.model.PayQueryResult;
import cn.hg.aifei.pay.model.PayRefundResult;
import java.util.Map;

/**
 * 已登记但尚未实装适配器的通道占位实现。
 */
public final class UnimplementedPayClient implements PayClient {

    private final String channelName;
    private final String type;

    public UnimplementedPayClient(String channelName, String type) {
        this.channelName = channelName;
        this.type = type;
    }

    @Override
    public PayOrderResult createOrder(String merchantId, String outTradeNo, long amountFen, String subject,
                                      String payType, String returnUrl, String notifyUrl,
                                      Map<String, String> options) {
        throw unimplemented();
    }

    @Override
    public PayQueryResult query(String merchantId, String outTradeNo) {
        throw unimplemented();
    }

    @Override
    public PayRefundResult refund(String merchantId, String outTradeNo, String outRequestNo, long refundFen,
                                  String refundReason) {
        throw unimplemented();
    }

    @Override
    public String channelName() {
        return channelName;
    }

    private PayException unimplemented() {
        return new PayException("支付通道适配器未落地：" + channelName + "（type=" + type + "）");
    }
}
