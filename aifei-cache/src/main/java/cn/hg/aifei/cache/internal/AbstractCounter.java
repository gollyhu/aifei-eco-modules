/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import cn.hg.aifei.cache.Counter;
import java.time.Duration;

/**
 * 本地与分布式计数实现的公共基类：集中承载与具体存储无关的实例元数据，以及计数物理 key 拼装。
 *
 * <p>基类只做两件事：保存实例级只读自述（命名空间、默认 TTL）并提供访问器；以及作为计数物理 key
 * 拼装的唯一定义点。计数读写、名称登记与清理一律由子类实现 —— 两个后端的计数名称集合存储机制不同
 * （进程内集合 vs Redis 集合），不存在可下沉的公共实现。</p>
 */
public abstract class AbstractCounter implements Counter {

    /**
     * 命名空间；子类拼装计数物理 key 或组合键时按名引用，故为 {@code protected}。
     */
    protected final String namespace;

    /**
     * 默认 TTL，单位毫秒。
     */
    private final long defaultTtlMillis;

    /**
     * 保存实例级配置；<b>只赋值，不校验</b>。
     *
     * <p>各子类的参数校验顺序与异常消息彼此不同（例如 Caffeine 先校验容量再校验命名空间，Redis
     * 先校验客户端再校验命名空间），校验若上移会静默改变可观察行为，因此留在子类构造器内。</p>
     *
     * @param namespace 命名空间
     * @param defaultTtlMillis 默认 TTL，单位毫秒
     */
    protected AbstractCounter(String namespace, long defaultTtlMillis) {
        this.namespace = namespace;
        this.defaultTtlMillis = defaultTtlMillis;
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

    // ─────────── 计数物理 key：本类的唯一定义点 ───────────

    /**
     * 拼装计数物理 key；【实现专用】计数物理 key 格式的唯一定义处。
     *
     * @param namespace 命名空间
     * @param counterName 计数名称
     * @param key 计数键
     * @return 物理 key
     */
    public static String physicalKey(String namespace, String counterName, String key) {
        return namespace + ":" + Config.COUNTER_KEY_PREFIX + counterName + ":" + key;
    }
}
