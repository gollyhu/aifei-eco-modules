/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import java.util.Map;

/**
 * 通道验签器：公共验签入口与 Handler 共用的内部契约。
 */
public interface PayChannelVerifier {

    boolean verify(String merchantId, Map<String, String> params);
}
