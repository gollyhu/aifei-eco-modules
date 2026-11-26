/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.aifei;

import cn.aifei.core.Handler;
import cn.aifei.core.Input;
import cn.aifei.core.Output;
import cn.hg.aifei.sms.SmsException;
import cn.hg.aifei.sms.internal.SmsChannelConfig;
import cn.hg.aifei.sms.internal.SmsClients;
import cn.hg.aifei.sms.internal.sms4j.AliyunReceiptVerifier;
import cn.hg.aifei.sms.internal.sms4j.SmsMappers;
import cn.hg.aifei.sms.internal.sms4j.SmsSupplierTypes;
import cn.hg.aifei.sms.spi.SmsReceipt;
import cn.hg.aifei.sms.spi.SmsReceiptListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 回执接收 Handler（接入层旁路）：按 URL 路由通道 → 验签 → 解析 → 监听器交付 → 响应闭环。
 *
 * <p>匹配到短信回调路径即自行读写响应并结束交换，不再调用 {@code next}；非短信路径交 {@code next}。
 * 注册顺序敏感：须注册在渲染首页的 Handler 之前（对齐 {@code WeixinHandler}）。本类不落库、不做幂等去重。</p>
 *
 * @param <I> 内核 Input 实现
 * @param <O> 内核 Output 实现
 */
public final class SmsHandler<I extends Input, O extends Output> extends Handler<I, O> {

    /** 默认回调前缀。 */
    public static final String DEFAULT_PREFIX = "/sms/callback";

    private static final String SUCCESS_JSON = "{\"Status\":\"SUCCESS\"}";
    private static final String FAIL_JSON = "{\"Status\":\"FAIL\"}";
    private static final String VERIFY_SECRET_KEY = "access-key-secret";

    private final String prefix;
    private final SmsIo<I> io;
    private final List<SmsReceiptListener> listeners;
    private final AliyunReceiptVerifier verifier = new AliyunReceiptVerifier();

    private volatile Throwable lastListenerError;

    /**
     * 默认前缀构造。
     *
     * @param io       传输桥接
     * @param listeners 回执监听器列表（可为空列表）
     */
    public SmsHandler(SmsIo<I> io, List<SmsReceiptListener> listeners) {
        this(DEFAULT_PREFIX, io, listeners);
    }

    /**
     * 自定义前缀构造。
     *
     * @param prefix    回调前缀，构造期归一化（trim、去尾斜杠、补前导斜杠）
     * @param io        传输桥接
     * @param listeners 回执监听器列表（可为空列表）
     */
    public SmsHandler(String prefix, SmsIo<I> io, List<SmsReceiptListener> listeners) {
        this.prefix = normalizePrefix(prefix);
        this.io = Objects.requireNonNull(io, "io can not be null");
        Objects.requireNonNull(listeners, "listeners can not be null");
        this.listeners = new ArrayList<SmsReceiptListener>(listeners);
    }

    @Override
    public void handle(String path, I in, O out) throws Exception {
        if (path == null || !path.startsWith(prefix + "/")) {
            if (next != null) {
                next.handle(path, in, out);
            }
            return;
        }
        String channel = channelOf(path);
        SmsChannelConfig config = channel == null ? null : SmsClients.config(channel);
        if (config == null || !SmsClients.isStarted()) {
            io.reply(in, 404, FAIL_JSON);
            return;
        }
        Map<String, String> params;
        String body;
        try {
            params = io.params(in);
            body = io.body(in);
        } catch (RuntimeException e) {
            io.reply(in, 500, FAIL_JSON);
            return;
        }
        if (!verified(config, params)) {
            io.reply(in, 403, FAIL_JSON);
            return;
        }
        try {
            SmsReceipt receipt = SmsMappers.toReceipt(channel, params, body);
            boolean listenerFailed = false;
            for (SmsReceiptListener listener : listeners) {
                try {
                    listener.onReceipt(receipt);
                } catch (RuntimeException e) {
                    lastListenerError = e;
                    listenerFailed = true;
                }
            }
            io.reply(in, listenerFailed ? 500 : 200, listenerFailed ? FAIL_JSON : SUCCESS_JSON);
        } catch (SmsException e) {
            io.reply(in, 500, FAIL_JSON);
        } catch (RuntimeException e) {
            io.reply(in, 500, FAIL_JSON);
        }
    }

    /**
     * @return 最近一次监听器异常（无则为 {@code null}），便于消费方排障
     */
    public Throwable lastListenerError() {
        return lastListenerError;
    }

    private boolean verified(SmsChannelConfig config, Map<String, String> params) {
        if (!SmsSupplierTypes.isSupported(config.getType())) {
            return false;
        }
        String secret = config.getOption(VERIFY_SECRET_KEY);
        return verifier.verify(params, secret);
    }

    private String channelOf(String path) {
        String rest = path.substring(prefix.length());
        if (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        int end = rest.indexOf('/');
        String channel = end < 0 ? rest : rest.substring(0, end);
        return channel.isEmpty() ? null : channel;
    }

    private static String normalizePrefix(String prefix) {
        String value = Objects.requireNonNull(prefix, "prefix can not be null").trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("prefix can not be blank");
        }
        return value.startsWith("/") ? value : "/" + value;
    }
}
