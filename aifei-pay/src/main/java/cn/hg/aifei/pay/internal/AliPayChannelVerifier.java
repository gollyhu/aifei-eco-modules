/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import java.util.Map;

/**
 * 支付宝验签器：把现有验签核心适配为通道契约。
 */
public final class AliPayChannelVerifier implements PayChannelVerifier {

    private final PayVerifier delegate;

    public AliPayChannelVerifier(PayVerifier delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate 不能为 null");
        }
        this.delegate = delegate;
    }

    @Override
    public boolean verify(String merchantId, Map<String, String> params) {
        return delegate.verify(merchantId, params);
    }

    public PayVerifier delegate() {
        return delegate;
    }
}
