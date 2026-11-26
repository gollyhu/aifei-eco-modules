/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import cn.hg.aifei.pay.internal.PayChannelVerifier;
import cn.hg.aifei.pay.internal.PayTypes;
import java.util.Map;

/**
 * 七渠道共用验签器：按通道类型分派到各内核的验签实现，与 {@code PayNotifyUtil} 共用此入口。
 */
public final class ChannelVerifier implements PayChannelVerifier {

    private final ChannelConfig config;

    public ChannelVerifier(ChannelConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config 不能为 null");
        }
        this.config = config;
    }

    @Override
    public boolean verify(String merchantId, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return false;
        }
        String type = config.type();
        if (PayTypes.QQ.equals(type)) {
            return QqKernel.verify(config, params);
        }
        if (PayTypes.JDPAY.equals(type)) {
            return JdKernel.verify(config, params);
        }
        if (PayTypes.UNIONPAY.equals(type)) {
            return UnionPayKernel.verify(config, params);
        }
        if (PayTypes.PAYPAL.equals(type)) {
            return PayPalKernel.verify(config, params);
        }
        if (PayTypes.STRIPE.equals(type)) {
            return StripeKernel.verify(config, params);
        }
        if (PayTypes.FUIOU.equals(type)) {
            return FuiYouKernel.verify(config, params);
        }
        if (PayTypes.XPAY.equals(type)) {
            return XPayKernel.verify(config, params);
        }
        return false;
    }
}
