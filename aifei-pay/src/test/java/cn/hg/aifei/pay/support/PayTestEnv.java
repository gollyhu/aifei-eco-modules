/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.support;

import cn.hg.aifei.pay.PayClient;
import cn.hg.aifei.pay.internal.AliPayChannelVerifier;
import cn.hg.aifei.pay.internal.PayChannelRegistry;
import cn.hg.aifei.pay.internal.PayConfig;
import cn.hg.aifei.pay.internal.PayVerifier;
import cn.hg.aifei.pay.model.PayOrderResult;
import cn.hg.aifei.pay.model.PayQueryResult;
import cn.hg.aifei.pay.model.PayRefundResult;
import java.util.Map;
import java.util.Properties;

/**
 * 验签用例的装配夹具：解析配置、建验签器、装入门面持有器，使 {@code PayNotifyUtil} 可用。
 */
public final class PayTestEnv {

    private static final String CHANNEL = "alipay";

    /**
     * 哑客户端：验签路径不应触碰交易动作。
     */
    private static final PayClient UNUSED_CLIENT = new PayClient() {

        @Override
        public PayOrderResult createOrder(String merchantId, String outTradeNo, long amountFen, String subject,
                                          String payType, String returnUrl, String notifyUrl,
                                          Map<String, String> options) {
            throw new UnsupportedOperationException("验签用例不应调用交易方法：createOrder");
        }

        @Override
        public PayQueryResult query(String merchantId, String outTradeNo) {
            throw new UnsupportedOperationException("验签用例不应调用交易方法：query");
        }

        @Override
        public PayRefundResult refund(String merchantId, String outTradeNo, String outRequestNo, long refundFen,
                                      String refundReason) {
            throw new UnsupportedOperationException("验签用例不应调用交易方法：refund");
        }

        @Override
        public String channelName() {
            return CHANNEL;
        }
    };

    private PayTestEnv() {
    }

    /**
     * 解析配置并装配验签器。
     *
     * @param properties 配置源
     * @return 配置域，供用例读取期望值
     */
    public static PayConfig install(Properties properties) {
        PayConfig config = PayConfig.parse(properties);
        PayChannelRegistry.clear();
        PayChannelRegistry.register(CHANNEL, UNUSED_CLIENT,
                new AliPayChannelVerifier(new PayVerifier(config.apps())));
        PayChannelRegistry.registerDefault(CHANNEL);
        PayChannelRegistry.setStarted(true);
        return config;
    }

    /**
     * 清空门面持有器，恢复「未装配」状态。
     */
    public static void clear() {
        PayChannelRegistry.clear();
    }
}
