/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import java.util.Map;

/**
 * 未落地通道的验签器占位：验签一律失败。
 */
public final class UnimplementedPayVerifier implements PayChannelVerifier {

    @Override
    public boolean verify(String merchantId, Map<String, String> params) {
        return false;
    }
}
