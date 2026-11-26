/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import cn.hg.aifei.pay.internal.PayChannelRegistry;
import java.util.Set;

/**
 * 支付门面：按通道名取用已装配的客户端。
 */
public final class PayKit {

    public static final String GATEWAY_SANDBOX = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    public static final String GATEWAY_PRODUCTION = "https://openapi.alipay.com/gateway.do";

    private PayKit() {
    }

    /**
     * @return 默认通道客户端
     */
    public static PayClient defaultClient() {
        return PayChannelRegistry.defaultClient();
    }

    /**
     * @param channelName 通道名
     * @return 命名通道客户端
     */
    public static PayClient use(String channelName) {
        return PayChannelRegistry.use(channelName);
    }

    /**
     * @return 已装配的通道名集合
     */
    public static Set<String> channels() {
        return PayChannelRegistry.channels();
    }
}
