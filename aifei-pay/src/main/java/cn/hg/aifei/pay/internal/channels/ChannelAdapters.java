/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import cn.hg.aifei.pay.PayException;
import cn.hg.aifei.pay.internal.PayTypes;

/**
 * 七渠道适配器工厂：按 type 构建内核与验签器，装配失败不留半装配态。
 */
public final class ChannelAdapters {

    private ChannelAdapters() {
    }

    public static ChannelPayClient createClient(ChannelConfig config) {
        return new ChannelPayClient(config, createKernel(config.type()));
    }

    public static ChannelVerifier createVerifier(ChannelConfig config) {
        return new ChannelVerifier(config);
    }

    private static ChannelKernel createKernel(String type) {
        try {
            if (PayTypes.QQ.equals(type)) {
                return new QqKernel();
            }
            if (PayTypes.JDPAY.equals(type)) {
                return new JdKernel();
            }
            if (PayTypes.UNIONPAY.equals(type)) {
                return new UnionPayKernel();
            }
            if (PayTypes.PAYPAL.equals(type)) {
                return new PayPalKernel();
            }
            if (PayTypes.STRIPE.equals(type)) {
                return new StripeKernel();
            }
            if (PayTypes.FUIOU.equals(type)) {
                return new FuiYouKernel();
            }
            if (PayTypes.XPAY.equals(type)) {
                return new XPayKernel();
            }
        } catch (LinkageError e) {
            throw new PayException("缺少支付通道依赖：type=" + type + "，请在消费方声明对应 optional 依赖", e);
        }
        throw new PayException("支付通道适配器未落地：" + type);
    }
}
