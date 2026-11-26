/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.aifei;

import cn.hg.aifei.pay.PayException;
import cn.hg.aifei.pay.PayNotifyUtil;
import cn.hg.aifei.pay.model.PayNotifyRequest;
import cn.hg.aifei.pay.spi.PayNotify;
import cn.hg.aifei.pay.spi.PayNotifyListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 回调入站闭环：读请求 → 验签 → 分发监听器 → 写渠道响应。
 */
public final class PayHandler {

    private static final String PREFIX = "/pay/notify/";

    private final PayIo io;
    private final List<PayNotifyListener> listeners;

    public PayHandler(PayIo io, List<PayNotifyListener> listeners) {
        if (io == null) {
            throw new IllegalArgumentException("io 不能为 null");
        }
        this.io = io;
        this.listeners = listeners == null
                ? Collections.<PayNotifyListener>emptyList()
                : Collections.unmodifiableList(new ArrayList<PayNotifyListener>(listeners));
    }

    public void handle() {
        String channel = channel(io.path());
        PayNotifyRequest request = request(io);
        String merchantId = io.param("merchantId");
        try {
            PayNotify notify = PayNotifyUtil.verifyAndParse(channel, merchantId, request);
            for (PayNotifyListener listener : listeners) {
                listener.onNotify(notify);
            }
            io.write(200, success(channel));
        } catch (PayException e) {
            io.write(400, failure(channel));
        }
    }

    private static String channel(String path) {
        if (path == null || !path.startsWith(PREFIX) || path.length() <= PREFIX.length()) {
            throw new PayException("回调路径非法：" + path);
        }
        return path.substring(PREFIX.length());
    }

    private static PayNotifyRequest request(PayIo io) {
        String body = io.body();
        if (body != null && !body.trim().isEmpty()) {
            return PayNotifyRequest.of(io.headers(), body);
        }
        return PayNotifyRequest.of(io.params());
    }

    private static String success(String channel) {
        return "wxpay".equals(channel) ? "{\"code\":\"SUCCESS\",\"message\":\"成功\"}" : "success";
    }

    private static String failure(String channel) {
        return "wxpay".equals(channel) ? "{\"code\":\"FAIL\",\"message\":\"失败\"}" : "failure";
    }
}
