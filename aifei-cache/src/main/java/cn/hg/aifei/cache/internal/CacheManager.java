/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.Counter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cache 实例注册表：持有「实例名 → (Cache, Counter)」，是 {@code CacheUtil} / {@code CounterUtil} 取用实例
 * 的唯一数据源。
 */
public final class CacheManager {

    /**
     * 注册表锁，register / clear / getCache / getCounter 共用。
     */
    private static final Object LOCK = new Object();

    /**
     * 实例名到 (Cache, Counter) 的映射；保持注册顺序以便异常消息稳定。
     */
    private static final Map<String, Entry> REGISTRY = new LinkedHashMap<>();

    /**
     * 注册表是否已写入过实例；{@link #clear()} 复位，{@link #lookup(String)} 据此判定生命周期。
     */
    private static boolean initialized = false;

    /**
     * 禁止创建工具类实例。
     */
    private CacheManager() {
    }

    /**
     * 注册一个实例：登记 {@code Cache} 并派生其同源 {@code Counter}，同时完成唯一名排他校验。
     *
     * <p>校验顺序固定：实例名 → {@code cache} 非空 → 实现包内 {@code CounterFactory} → 同名重复
     * （抛 {@link IllegalStateException}）→ 排他 → 派生出的 {@code counter} 非空。</p>
     *
     * @param instanceName 实例名
     * @param cache 该实例的缓存实现
     */
    public static void register(String instanceName, Cache cache) {
        synchronized (LOCK) {
            Config.requireRegisteredInstanceName(instanceName);
            if (cache == null) {
                throw new IllegalArgumentException("cache can not be null");
            }
            if (!(cache instanceof CounterFactory)) {
                throw new IllegalArgumentException(
                        "Unsupported cache implementation for automatic counter creation: "
                                + cache.getClass().getName()
                );
            }
            if (REGISTRY.containsKey(instanceName)) {
                throw new IllegalStateException("instance name already registered: " + instanceName);
            }
            requireExclusiveKeySpace(instanceName, cache);
            Counter counter = ((CounterFactory) cache).createCounter();
            if (counter == null) {
                throw new IllegalArgumentException("counter can not be null");
            }
            REGISTRY.put(instanceName, new Entry(cache, counter));
            initialized = true;
        }
    }

    /**
     * 校验「唯一名 + {@code namespace}」在注册表内唯一：两者都相同即冲突。唯一名由能力接口
     * {@link UniqueNameAware} 声明；未实现该接口、或其返回 {@code null}/空白的实例不参与排他判定。
     */
    private static void requireExclusiveKeySpace(String instanceName, Cache cache) {
        if (!(cache instanceof UniqueNameAware)) {
            return;
        }
        String uniqueName = ((UniqueNameAware) cache).uniqueName();
        if (isNotParticipating(uniqueName)) {
            return;
        }
        String namespace = cache.namespace();
        for (Map.Entry<String, Entry> existing : REGISTRY.entrySet()) {
            Cache other = existing.getValue().cache;
            if (!(other instanceof UniqueNameAware)) {
                continue;
            }
            String otherUniqueName = ((UniqueNameAware) other).uniqueName();
            if (isNotParticipating(otherUniqueName)) {
                continue;
            }
            if (uniqueName.equals(otherUniqueName) && namespace.equals(other.namespace())) {
                throw new IllegalArgumentException(
                        "duplicate key space: instance \"" + existing.getKey() + "\" and instance \""
                                + instanceName + "\" both resolve to " + uniqueName
                                + " with namespace \"" + namespace + "\""
                );
            }
        }
    }

    /**
     * 实现了 {@link UniqueNameAware} 但唯一名为 {@code null} 或空白时，该实例不参与排他判定。
     */
    private static boolean isNotParticipating(String uniqueName) {
        return uniqueName == null || uniqueName.trim().isEmpty();
    }

    /**
     * 清空注册表；清空后取用入口立即失效。
     */
    public static void clear() {
        synchronized (LOCK) {
            REGISTRY.clear();
            initialized = false;
        }
    }

    /**
     * 取指定实例的 {@code Cache}。
     */
    public static Cache getCache(String instanceName) {
        synchronized (LOCK) {
            return lookup(instanceName).cache;
        }
    }

    /**
     * 取指定实例同源派生的 {@code Counter}。
     */
    public static Counter getCounter(String instanceName) {
        synchronized (LOCK) {
            return lookup(instanceName).counter;
        }
    }

    /**
     * 取用期判定，先生命周期后参数。
     */
    private static Entry lookup(String instanceName) {
        if (!initialized) {
            throw new IllegalStateException(
                    "CacheManager has not been initialized. Add CachePlugin first."
            );
        }
        Config.requireRegisteredInstanceName(instanceName);
        Entry entry = REGISTRY.get(instanceName);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "unknown cache instance: " + instanceName
                            + ", registered instances: " + new ArrayList<>(REGISTRY.keySet())
            );
        }
        return entry;
    }

    /**
     * 单个实例的 (Cache, Counter) 配对。
     */
    private static final class Entry {

        private final Cache cache;

        private final Counter counter;

        private Entry(Cache cache, Counter counter) {
            this.cache = cache;
            this.counter = counter;
        }
    }
}
