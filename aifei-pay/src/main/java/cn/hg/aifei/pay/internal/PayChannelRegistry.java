/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import cn.hg.aifei.pay.PayClient;
import cn.hg.aifei.pay.PayException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通道注册表：按通道标识保存客户端与验签器，并维护默认通道与装配状态。
 */
public final class PayChannelRegistry {

    private static final ConcurrentHashMap<String, PayClient> CLIENTS =
            new ConcurrentHashMap<String, PayClient>();
    private static final ConcurrentHashMap<String, PayChannelVerifier> VERIFIERS =
            new ConcurrentHashMap<String, PayChannelVerifier>();
    private static volatile String defaultChannel;
    private static volatile boolean started;

    private PayChannelRegistry() {
    }

    public static void register(String name, PayClient client, PayChannelVerifier verifier) {
        CLIENTS.put(name, client);
        VERIFIERS.put(name, verifier);
    }

    public static void registerDefault(String name) {
        if (!CLIENTS.containsKey(name)) {
            throw new PayException("支付通道未登记：" + name);
        }
        defaultChannel = name;
    }

    public static PayClient defaultClient() {
        requireStarted();
        PayClient client = defaultChannel == null ? null : CLIENTS.get(defaultChannel);
        if (client == null) {
            throw new PayException("支付默认通道未装配");
        }
        return client;
    }

    public static PayClient use(String name) {
        requireStarted();
        PayClient client = name == null ? null : CLIENTS.get(name);
        if (client == null) {
            throw new PayException("支付通道不存在：" + name);
        }
        return client;
    }

    public static PayChannelVerifier verifier(String name) {
        requireStarted();
        PayChannelVerifier verifier = name == null ? null : VERIFIERS.get(name);
        if (verifier == null) {
            throw new PayException("支付通道验签器不存在：" + name);
        }
        return verifier;
    }

    public static Set<String> channels() {
        requireStarted();
        return Collections.unmodifiableSet(new LinkedHashSet<String>(CLIENTS.keySet()));
    }

    public static boolean isStarted() {
        return started;
    }

    public static void setStarted(boolean value) {
        started = value;
    }

    public static void clear() {
        CLIENTS.clear();
        VERIFIERS.clear();
        defaultChannel = null;
        started = false;
    }

    private static void requireStarted() {
        if (!started) {
            throw new PayException(PayErrors.NOT_INSTALLED);
        }
    }
}
