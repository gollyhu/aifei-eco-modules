/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal;

import cn.hg.aifei.email.MailClient;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内通道注册表（非缓存出口）：按通道标识保存客户端实例，并维护默认通道与共享装配状态。
 *
 * <p>模块不自建线程池、不持久化；{@code started} 为共享状态标志，停止后所有门面调用经此标志失败
 * （即使注入的单例对象仍被持有）。</p>
 */
public final class MailClients {

    private static final ConcurrentHashMap<String, MailClient> CLIENTS =
            new ConcurrentHashMap<String, MailClient>();

    private static volatile String defaultChannelName;
    private static volatile boolean started;

    private MailClients() {
    }

    /**
     * 登记一个通道客户端。
     *
     * @param name   通道标识
     * @param client 客户端实例
     */
    public static void register(String name, MailClient client) {
        CLIENTS.put(name, client);
    }

    /**
     * 指定默认通道标识。
     *
     * @param name 已登记的通道标识
     */
    public static void registerDefault(String name) {
        defaultChannelName = name;
    }

    /**
     * 取默认通道客户端（每次现取，不缓存）。
     *
     * @return 默认通道客户端
     * @throws cn.hg.aifei.email.MailException 插件未启动或已停止、默认通道缺失
     */
    public static MailClient defaultClient() {
        requireStarted();
        String name = defaultChannelName;
        MailClient client = name == null ? null : CLIENTS.get(name);
        if (client == null) {
            throw MailErrors.notStarted();
        }
        return client;
    }

    /**
     * 按标识取命名通道客户端。
     *
     * @param name 通道标识
     * @return 命名通道客户端
     * @throws cn.hg.aifei.email.MailException 插件未启动或已停止、通道不存在
     */
    public static MailClient use(String name) {
        requireStarted();
        MailClient client = name == null ? null : CLIENTS.get(name);
        if (client == null) {
            throw MailErrors.channelNotFound(name);
        }
        return client;
    }

    /**
     * @return 已登记通道标识的不可变快照（保持登记顺序）
     * @throws cn.hg.aifei.email.MailException 插件未启动或已停止
     */
    public static Set<String> channels() {
        requireStarted();
        return Collections.unmodifiableSet(new LinkedHashSet<String>(CLIENTS.keySet()));
    }

    /**
     * @return 默认通道标识，未装配时为 {@code null}
     */
    public static String defaultChannelName() {
        return defaultChannelName;
    }

    /**
     * @return 当前是否已装配且未停止
     */
    public static boolean isStarted() {
        return started;
    }

    /**
     * 更新共享装配状态；由装配入口的启动 / 停止方法调用。
     *
     * @param value 装配状态
     */
    public static void setStarted(boolean value) {
        started = value;
    }

    /**
     * 清空全部登记项、默认通道并复位装配状态（失败回滚 / 停止）。
     */
    public static void clear() {
        CLIENTS.clear();
        defaultChannelName = null;
        started = false;
    }

    /**
     * 供诊断使用的登记项快照。
     *
     * @return 通道标识到客户端实例的不可变副本
     */
    public static Map<String, MailClient> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, MailClient>(CLIENTS));
    }

    private static void requireStarted() {
        if (!started) {
            throw MailErrors.notStarted();
        }
    }
}
