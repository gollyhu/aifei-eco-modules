/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.support;

import cn.hg.aifei.pay.AliPayClient;
import cn.hg.aifei.pay.internal.PayConfig;
import cn.hg.aifei.pay.internal.PayHolder;
import cn.hg.aifei.pay.internal.PayVerifier;
import cn.hg.aifei.pay.model.PayOrderResult;
import cn.hg.aifei.pay.model.PayQueryResult;
import cn.hg.aifei.pay.model.PayRefundResult;
import java.util.Properties;

/**
 * 验签用例的装配夹具：解析配置、建验签器、装入门面持有器，使 {@code AliPayNotifyUtil} 可用。
 *
 * <p>验签用例不需要交易客户端，因此这里装入一个「被调用即失败」的哑客户端：一旦有用例误触交易方法，
 * 会立刻以 {@link UnsupportedOperationException} 暴露，而不是静默返回 {@code null} 让断言失真。</p>
 */
public final class PayTestEnv {

    /**
     * 哑客户端：验签路径不应触碰交易动作。
     */
    private static final AliPayClient UNUSED_CLIENT = new AliPayClient() {

        @Override
        public PayOrderResult precreate(String appId, String outTradeNo, long amountFen, String subject,
                                        String notifyUrl) {
            throw new UnsupportedOperationException("验签用例不应调用交易方法：precreate");
        }

        @Override
        public PayOrderResult wapPay(String appId, String outTradeNo, long amountFen, String subject,
                                     String returnUrl, String notifyUrl) {
            throw new UnsupportedOperationException("验签用例不应调用交易方法：wapPay");
        }

        @Override
        public PayOrderResult pagePay(String appId, String outTradeNo, long amountFen, String subject,
                                      String returnUrl, String notifyUrl) {
            throw new UnsupportedOperationException("验签用例不应调用交易方法：pagePay");
        }

        @Override
        public PayQueryResult query(String appId, String outTradeNo) {
            throw new UnsupportedOperationException("验签用例不应调用交易方法：query");
        }

        @Override
        public PayRefundResult refund(String appId, String outTradeNo, String outRequestNo, long refundFen,
                                      String refundReason) {
            throw new UnsupportedOperationException("验签用例不应调用交易方法：refund");
        }
    };

    /**
     * 禁止创建工具类实例。
     */
    private PayTestEnv() {
    }

    /**
     * 解析配置并装配验签器。
     *
     * @param properties 配置源
     * @return 配置域，供用例读取期望值（如证书序列号）
     */
    public static PayConfig install(Properties properties) {
        PayConfig config = PayConfig.parse(properties);
        PayHolder.install(UNUSED_CLIENT, new PayVerifier(config.apps()));
        return config;
    }

    /**
     * 清空门面持有器，恢复「未装配」状态。
     */
    public static void clear() {
        PayHolder.clear();
    }
}
