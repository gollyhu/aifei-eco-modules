/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import cn.hg.aifei.cache.Cache;
import java.time.Duration;

/**
 * 本地与分布式缓存实现的公共基类：集中承载与具体存储无关的实例元数据，以及存储值编解码与物理 key 拼装。
 *
 * <p>基类只做两件事：保存实例级只读自述（命名空间、默认 TTL、{@code cacheNull}）并提供访问器；
 * 以及作为哨兵编解码与物理 key 拼装的唯一定义点。存储读写、清理、计数等能力一律由子类实现。</p>
 */
public abstract class AbstractCache implements Cache {

    /**
     * 命名空间；子类拼装物理 key 与组合键时按名引用，故为 {@code protected}。
     */
    protected final String namespace;

    /**
     * 默认 TTL，单位毫秒。
     */
    private final long defaultTtlMillis;

    /**
     * 是否缓存 {@code null} 值。
     */
    private final boolean cacheNull;

    /**
     * 保存实例级配置；<b>只赋值，不校验</b>。
     *
     * <p>各子类的参数校验顺序与异常消息彼此不同（例如 Caffeine 先校验容量再校验命名空间，Redis
     * 先校验配置非空再校验命名空间），校验若上移会静默改变可观察行为，因此留在子类构造器内。</p>
     *
     * @param namespace 命名空间
     * @param defaultTtlMillis 默认 TTL，单位毫秒
     * @param cacheNull 是否缓存 {@code null} 值
     */
    protected AbstractCache(String namespace, long defaultTtlMillis, boolean cacheNull) {
        this.namespace = namespace;
        this.defaultTtlMillis = defaultTtlMillis;
        this.cacheNull = cacheNull;
    }

    /**
     * 返回该实例的命名空间。
     */
    @Override
    public final String namespace() {
        return namespace;
    }

    /**
     * 返回该实例的默认 TTL。
     */
    @Override
    public final Duration ttl() {
        return Duration.ofMillis(defaultTtlMillis);
    }

    /**
     * 返回该实例是否缓存 {@code null} 值。
     */
    @Override
    public final boolean cacheNull() {
        return cacheNull;
    }

    // ─────────── 存储值编解码与物理 key：本类的唯一定义点 ───────────

    /**
     * 业务值转换为实际写入存储的值；【实现专用】唯一决策点，实现类不得自行判断。
     *
     * <p>非 {@code null} 值原样返回；{@code null} 且 {@code cacheNull = true} 时返回哨兵
     * {@link NullValue#INSTANCE}；{@code null} 且 {@code cacheNull = false} 时返回 {@code null}，
     * 表示该值不应写入存储，调用方必须跳过写入且不抛异常。「不写入」= 保持现状，目标 key 上既有
     * 的值不受影响。</p>
     *
     * @param value 业务值
     * @param cacheNull 是否缓存 {@code null} 值
     * @return 实际写入存储的值；返回 {@code null} 表示不应写入
     */
    public static Object toStoredValue(Object value, boolean cacheNull) {
        if (value != null) {
            return value;
        }
        return cacheNull ? NullValue.INSTANCE : null;
    }

    /**
     * 存储值转换为业务值，命中哨兵时对调用方透明地返回 {@code null}；【实现专用】存储值解码的
     * 唯一定义点，实现类不得自行判断。
     *
     * @param stored 存储值
     * @param <T> 期望的值类型
     * @return 业务值
     */
    public static <T> T fromStoredValue(Object stored) {
        return stored == NullValue.INSTANCE ? null : (T) stored;
    }

    /**
     * 拼装缓存物理 key；【实现专用】物理 key 格式的唯一定义处。
     *
     * @param namespace 命名空间
     * @param cacheName 缓存名
     * @param key 缓存键
     * @return 物理 key
     */
    public static String physicalKey(String namespace, String cacheName, String key) {
        return namespace + ":" + cacheName + ":" + key;
    }
}
