/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.caffeine;

import cn.hg.aifei.cache.internal.AbstractCounter;
import cn.hg.aifei.cache.internal.CacheValidator;
import cn.hg.aifei.cache.internal.Config;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 的进程内计数实现。
 */
public class CaffeineCounter extends AbstractCounter {

    private final com.github.benmanes.caffeine.cache.Cache<CaffeineCacheKey, Long> counters;

    private final Policy.VarExpiration<CaffeineCacheKey, Long> expiration;

    private final CaffeineLocks locks = new CaffeineLocks();

    /**
     * 曾被创建过的计数名称集合；由 {@code namesLock} 保护。
     */
    private final Set<String> counterNames = new LinkedHashSet<>();

    /**
     * 计数名称集合的专用锁，避免与业务条目的分段锁产生嵌套。
     */
    private final Object namesLock = new Object();

    /**
     * 使用默认容量与默认 TTL 创建计数器。
     */
    public CaffeineCounter() {
        this(CaffeineConfig.DEFAULT_MAX_SIZE, Ticker.systemTicker(), Config.DEFAULT_NAMESPACE, Config.DEFAULT_TTL);
    }

    /**
     * 使用指定容量与默认 TTL 创建计数器。
     *
     * @param maximumSize 最大计数项数量
     */
    public CaffeineCounter(long maximumSize) {
        this(maximumSize, Ticker.systemTicker(), Config.DEFAULT_NAMESPACE, Config.DEFAULT_TTL);
    }

    /**
     * 使用指定容量、时钟与默认 TTL 创建计数器，供确定性过期测试使用（包内可见）。
     */
    CaffeineCounter(long maximumSize, Ticker ticker) {
        this(maximumSize, ticker, Config.DEFAULT_NAMESPACE, Config.DEFAULT_TTL);
    }

    /**
     * 使用指定容量、时钟、命名空间与默认 TTL 创建计数器（包内可见）。
     */
    CaffeineCounter(long maximumSize, Ticker ticker, String namespace) {
        this(maximumSize, ticker, namespace, Config.DEFAULT_TTL);
    }

    /**
     * 使用指定容量、时钟、命名空间与实例默认 TTL 创建计数器（包内可见）。
     *
     * @param maximumSize 最大计数项数量
     * @param ticker 时钟
     * @param namespace 命名空间
     * @param ttlMillis 实例默认 TTL，单位毫秒
     */
    CaffeineCounter(long maximumSize, Ticker ticker, String namespace, long ttlMillis) {
        super(namespace, ttlMillis);
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("maximumSize must be greater than zero");
        }
        Objects.requireNonNull(namespace, "namespace can not be null");
        this.counters = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .ticker(Objects.requireNonNull(ticker, "ticker can not be null"))
                .expireAfter(CaffeineCounterExpiry.INSTANCE)
                .build();

        this.expiration = counters.policy()
                .expireVariably()
                .orElseThrow(() -> new IllegalStateException("Variable expiration is not available"));
    }

    /**
     * 从 Caffeine 读取计数值。
     */
    @Override
    public Long get(String counterName, String key) {
        if (CacheValidator.isInvalidCounterNameAndKey(counterName, key)) {
            return null;
        }
        CaffeineCacheKey counterKey = new CaffeineCacheKey(namespace, counterName, key);
        synchronized (locks.forKey(counterKey)) {
            return counters.getIfPresent(counterKey);
        }
    }

    /**
     * 增加计数值，固定窗口语义。
     */
    @Override
    public long increase(String counterName, String key, long step, Duration ttl) {
        return updateCounter(counterName, key, step, ttl, true, false);
    }

    /**
     * 增加计数值并刷新 TTL。
     */
    @Override
    public long increaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
        return updateCounter(counterName, key, step, ttl, true, true);
    }

    /**
     * 减少计数值，固定窗口语义。
     */
    @Override
    public long decrease(String counterName, String key, long step, Duration ttl) {
        return updateCounter(counterName, key, step, ttl, false, false);
    }

    /**
     * 减少计数值并刷新 TTL。
     */
    @Override
    public long decreaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
        return updateCounter(counterName, key, step, ttl, false, true);
    }

    /**
     * 删除单个计数项。
     */
    @Override
    public void remove(String counterName, String key) {
        if (CacheValidator.isInvalidCounterNameAndKey(counterName, key)) {
            return;
        }
        CaffeineCacheKey counterKey = new CaffeineCacheKey(namespace, counterName, key);
        synchronized (locks.forKey(counterKey)) {
            counters.invalidate(counterKey);
        }
    }

    /**
     * 清空指定计数名称下的全部计数项，并把该计数名称从 {@link #counterNames()} 中移除。
     */
    @Override
    public void clear(String counterName) {
        String validCounterName = CacheValidator.requireCounterName(counterName);
        List<CaffeineCacheKey> matches = new ArrayList<>();
        for (CaffeineCacheKey counterKey : counters.asMap().keySet()) {
            if (counterKey.belongsTo(namespace, validCounterName)) {
                matches.add(counterKey);
            }
        }
        for (CaffeineCacheKey counterKey : matches) {
            synchronized (locks.forKey(counterKey)) {
                counters.invalidate(counterKey);
            }
        }
        forgetCounterName(validCounterName);
    }

    /**
     * 返回曾被创建过的计数名称集合；可见范围是本进程。
     */
    @Override
    public Set<String> counterNames() {
        synchronized (namesLock) {
            return new LinkedHashSet<>(counterNames);
        }
    }

    /**
     * 使用分段锁原子更新或创建计数值。校验（含 TTL 上下限）全部在改写存储之前完成，因此超限 TTL
     * 不会改变已有计数的数值与截止点。
     */
    private long updateCounter(String counterName, String key, long step, Duration ttl,
                               boolean increase, boolean refreshTtl) {
        String validCounterName = CacheValidator.requireCounterName(counterName);
        String validKey = CacheValidator.requireKey(key);
        long validStep = CacheValidator.requireCounterStep(step);
        long ttlMillis = CacheValidator.requireTtl(ttl);
        CaffeineCacheKey counterKey = new CaffeineCacheKey(namespace, validCounterName, validKey);

        long newValue;
        boolean created;
        synchronized (locks.forKey(counterKey)) {
            Long value = counters.getIfPresent(counterKey);
            long currentValue = value == null ? 0L : value;
            newValue = increase
                    ? Math.addExact(currentValue, validStep)
                    : Math.subtractExact(currentValue, validStep);
            created = value == null;
            if (created || refreshTtl) {
                expiration.put(counterKey, newValue, ttlMillis, TimeUnit.MILLISECONDS);
            } else if (counters.asMap().replace(counterKey, newValue) == null) {
                // replace 保留原截止点；替换前已过期或被淘汰时，从 0 重新创建。
                newValue = increase ? validStep : -validStep;
                created = true;
                expiration.put(counterKey, newValue, ttlMillis, TimeUnit.MILLISECONDS);
            }
        }
        if (created) {
            rememberCounterName(validCounterName);
        }
        return newValue;
    }

    /**
     * 把计数名称登记进内部集合；已登记时是空操作。
     */
    private void rememberCounterName(String counterName) {
        synchronized (namesLock) {
            counterNames.add(counterName);
        }
    }

    /**
     * 从内部集合精确移除指定计数名称；没有该名称时是空操作。
     */
    private void forgetCounterName(String counterName) {
        synchronized (namesLock) {
            counterNames.remove(counterName);
        }
    }
}
