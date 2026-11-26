/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.internal;

import cn.hg.aifei.sms.SmsClient;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内通道注册表（非缓存出口）：按通道标识保存客户端实例与配置，并维护默认通道及装配状态。
 *
 * <p>模块不自建线程池、不持久化；{@code started} 为共享状态标志，{@code stop()} 后所有门面调用
 * 经此标志失败（即使 {@code @Inject} 单例对象仍被持有）。</p>
 */
public final class SmsClients {

    private static final ConcurrentHashMap<String, SmsClient> CLIENTS =
            new ConcurrentHashMap<String, SmsClient>();
    private static final ConcurrentHashMap<String, SmsChannelConfig> CONFIGS =
            new ConcurrentHashMap<String, SmsChannelConfig>();

    private static volatile SmsClient defaultClient;
    private static volatile boolean started;

    private SmsClients() {
    }

    /**
     * 登记一个通道客户端及其配置。
     *
     * @param name   通道标识
     * @param client 客户端实例
     * @param config 通道配置（供回执验签分流读取）
     */
    public static void register(String name, SmsClient client, SmsChannelConfig config) {
        CLIENTS.put(name, client);
        CONFIGS.put(name, config);
    }

    /**
     * 指定默认通道标识。
     *
     * @param name 已登记的通道标识
     */
    public static void registerDefault(String name) {
        defaultClient = CLIENTS.get(name);
    }

    /**
     * 取默认通道客户端。
     *
     * @return 默认通道客户端
     * @throws cn.hg.aifei.sms.SmsException 未装配 / 已停止
     */
    public static SmsClient defaultClient() {
        requireStarted();
        SmsClient client = defaultClient;
        if (client == null) {
            throw SmsErrors.notStarted();
        }
        return client;
    }

    /**
     * 按标识取命名通道客户端。
     *
     * @param name 通道标识
     * @return 命名通道客户端
     * @throws cn.hg.aifei.sms.SmsException 未装配 / 已停止 / 通道不存在
     */
    public static SmsClient use(String name) {
        requireStarted();
        SmsClient client = name == null ? null : CLIENTS.get(name);
        if (client == null) {
            throw SmsErrors.channelNotFound(name);
        }
        return client;
    }

    /**
     * @return 已登记通道标识的不可变快照
     * @throws cn.hg.aifei.sms.SmsException 未装配 / 已停止
     */
    public static Set<String> channels() {
        requireStarted();
        return Collections.unmodifiableSet(new LinkedHashSet<String>(CLIENTS.keySet()));
    }

    /**
     * 供回执 Handler 按通道读取配置（验签分流与密钥来源）。
     *
     * @param name 通道标识
     * @return 通道配置，未知通道返回 {@code null}
     */
    public static SmsChannelConfig config(String name) {
        return name == null ? null : CONFIGS.get(name);
    }

    /**
     * @return 当前是否已装配且未停止
     */
    public static boolean isStarted() {
        return started;
    }

    /**
     * 更新共享装配状态；由 {@code SmsPlugin.start()/stop()} 调用。
     *
     * @param value 装配状态
     */
    public static void setStarted(boolean value) {
        started = value;
    }

    /**
     * 清空全部登记项与默认通道并复位装配状态（失败回滚 / 停止）。
     */
    public static void clear() {
        CLIENTS.clear();
        CONFIGS.clear();
        defaultClient = null;
        started = false;
    }

    private static void requireStarted() {
        if (!started) {
            throw SmsErrors.notStarted();
        }
    }
}
