/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.caffeine;

import cn.hg.aifei.cache.Counter;
import cn.hg.aifei.cache.internal.AbstractCache;
import cn.hg.aifei.cache.internal.CacheValidator;
import cn.hg.aifei.cache.internal.Config;
import cn.hg.aifei.cache.internal.CounterFactory;
import cn.hg.aifei.cache.internal.KeyRanges;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 的进程内缓存实现。
 */
public class CaffeineCache extends AbstractCache implements CounterFactory {

    private final com.github.benmanes.caffeine.cache.Cache<CaffeineCacheKey, Object> cache;

    private final Policy.VarExpiration<CaffeineCacheKey, Object> expiration;

    private final CaffeineLocks locks = new CaffeineLocks();

    /**
     * 曾被写入过的缓存名集合；由 {@code namesLock} 保护。
     */
    private final Set<String> cacheNames = new LinkedHashSet<>();

    /**
     * 缓存名集合的专用锁，避免与业务条目的分段锁产生嵌套。
     */
    private final Object namesLock = new Object();

    private final long maximumSize;

    private final Ticker ticker;

    /**
     * 使用指定容量与默认实例级配置创建缓存。
     *
     * @param maximumSize 最大条目数
     */
    public CaffeineCache(long maximumSize) {
        this(maximumSize, Ticker.systemTicker(), Config.DEFAULT_NAMESPACE,
                Config.DEFAULT_TTL, false);
    }

    /**
     * 使用指定容量与实例级配置创建缓存。
     *
     * @param maximumSize 最大条目数
     * @param namespace 命名空间
     * @param ttlMillis 默认 TTL，单位毫秒
     * @param cacheNull 是否缓存 {@code null} 值
     */
    public CaffeineCache(long maximumSize, String namespace, long ttlMillis, boolean cacheNull) {
        this(maximumSize, Ticker.systemTicker(), namespace, ttlMillis, cacheNull);
    }

    /**
     * 使用指定容量、时钟与实例级配置创建缓存（包内可见，供测试注入可控时钟）。
     */
    CaffeineCache(long maximumSize, Ticker ticker, String namespace, long ttlMillis, boolean cacheNull) {
        super(namespace, ttlMillis, cacheNull);
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("maximumSize must be greater than zero");
        }
        CacheValidator.requireNamespace(namespace);
        this.maximumSize = maximumSize;
        this.ticker = Objects.requireNonNull(ticker, "ticker can not be null");

        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .ticker(this.ticker)
                .expireAfter(CaffeineExpiry.INSTANCE)
                .build();

        this.expiration = cache.policy()
                .expireVariably()
                .orElseThrow(() -> new IllegalStateException("Variable expiration is not available"));
    }

    /**
     * 创建使用相同容量、时钟、命名空间与默认 TTL 的计数器（不共享存储与锁）。
     *
     * @return 同源计数器
     */
    @Override
    public Counter createCounter() {
        return new CaffeineCounter(maximumSize, ticker, namespace, ttl().toMillis());
    }

    /**
     * 从 Caffeine 读取缓存值，命中哨兵时返回 {@code null}。
     */
    @Override
    public <T> T get(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return null;
        }
        CaffeineCacheKey cacheKey = new CaffeineCacheKey(namespace, cacheName, key);
        synchronized (locks.forKey(cacheKey)) {
            return fromStoredValue(cache.getIfPresent(cacheKey));
        }
    }

    /**
     * 存储级判定缓存项是否存在且未过期。
     */
    @Override
    public boolean exists(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return false;
        }
        CaffeineCacheKey cacheKey = new CaffeineCacheKey(namespace, cacheName, key);
        synchronized (locks.forKey(cacheKey)) {
            return cache.getIfPresent(cacheKey) != null;
        }
    }

    /**
     * 写入带有效期的缓存值；值被判定为不写入时跳过。
     */
    @Override
    public void put(String cacheName, String key, Object value, Duration ttl) {
        String validCacheName = CacheValidator.requireCacheName(cacheName);
        String validKey = CacheValidator.requireKey(key);
        long ttlMillis = CacheValidator.requireTtl(ttl);
        Object stored = toStoredValue(value, cacheNull());
        if (stored == null) {
            return;
        }

        CaffeineCacheKey cacheKey = new CaffeineCacheKey(namespace, validCacheName, validKey);
        synchronized (locks.forKey(cacheKey)) {
            expiration.put(cacheKey, stored, ttlMillis, TimeUnit.MILLISECONDS);
        }
        rememberCacheName(validCacheName);
    }

    /**
     * 当缓存项不存在或已过期时写入缓存值；命中哨兵视为已存在。
     */
    @Override
    public boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl) {
        String validCacheName = CacheValidator.requireCacheName(cacheName);
        String validKey = CacheValidator.requireKey(key);
        long ttlMillis = CacheValidator.requireTtl(ttl);
        Object stored = toStoredValue(value, cacheNull());
        if (stored == null) {
            return false;
        }

        CaffeineCacheKey cacheKey = new CaffeineCacheKey(namespace, validCacheName, validKey);
        boolean written;
        synchronized (locks.forKey(cacheKey)) {
            written = expiration.putIfAbsent(cacheKey, stored, ttlMillis, TimeUnit.MILLISECONDS);
        }
        if (written) {
            rememberCacheName(validCacheName);
        }
        return written;
    }

    /**
     * 重设已存在且未过期缓存项的剩余有效期；不存在或已过期时返回 {@code false}。
     */
    @Override
    public boolean expire(String cacheName, String key, Duration ttl) {
        String validCacheName = CacheValidator.requireCacheName(cacheName);
        String validKey = CacheValidator.requireKey(key);
        long ttlMillis = CacheValidator.requireTtl(ttl);

        CaffeineCacheKey cacheKey = new CaffeineCacheKey(namespace, validCacheName, validKey);
        synchronized (locks.forKey(cacheKey)) {
            // setExpiresAfter 会更新已过期但尚未清理的节点，先判断剩余时间避免复活过期项。
            OptionalLong remaining = expiration.getExpiresAfter(cacheKey, TimeUnit.NANOSECONDS);
            if (!remaining.isPresent() || remaining.getAsLong() <= 0L) {
                return false;
            }
            expiration.setExpiresAfter(cacheKey, ttlMillis, TimeUnit.MILLISECONDS);
            return true;
        }
    }

    /**
     * 删除单个缓存项。
     */
    @Override
    public void remove(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return;
        }
        CaffeineCacheKey cacheKey = new CaffeineCacheKey(namespace, cacheName, key);
        synchronized (locks.forKey(cacheKey)) {
            cache.invalidate(cacheKey);
        }
    }

    /**
     * 清空指定缓存名下的缓存项，并把该缓存名从 {@link #cacheNames()} 中移除。
     */
    @Override
    public void clear(String cacheName) {
        String validCacheName = CacheValidator.requireCacheName(cacheName);
        List<CaffeineCacheKey> keys = new ArrayList<>();
        for (CaffeineCacheKey key : cache.asMap().keySet()) {
            if (key.belongsTo(namespace, validCacheName)) {
                keys.add(key);
            }
        }
        for (CaffeineCacheKey key : keys) {
            synchronized (locks.forKey(key)) {
                cache.invalidate(key);
            }
        }
        forgetCacheName(validCacheName);
    }

    /**
     * 返回缓存项的剩余有效期；不存在或已过期时返回 {@code null}。
     */
    @Override
    public Duration getTtl(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return null;
        }
        CaffeineCacheKey cacheKey = new CaffeineCacheKey(namespace, cacheName, key);
        synchronized (locks.forKey(cacheKey)) {
            OptionalLong remaining = expiration.getExpiresAfter(cacheKey, TimeUnit.NANOSECONDS);
            if (!remaining.isPresent() || remaining.getAsLong() <= 0L) {
                return null;
            }
            return Duration.ofNanos(remaining.getAsLong());
        }
    }

    /**
     * 返回该缓存名下指定区间的键，按字典序升序；切片语义见 {@link KeyRanges#slice}。
     */
    @Override
    public List<String> keys(String cacheName, int begin, int end) {
        String validCacheName = CacheValidator.requireCacheName(cacheName);
        List<String> keys = new ArrayList<>();
        for (CaffeineCacheKey cacheKey : cache.asMap().keySet()) {
            if (cacheKey.belongsTo(namespace, validCacheName)) {
                keys.add(cacheKey.key());
            }
        }
        Collections.sort(keys);
        return KeyRanges.slice(keys, begin, end);
    }

    /**
     * 返回已被写入过的缓存名集合；可见范围是本进程。
     */
    @Override
    public Set<String> cacheNames() {
        synchronized (namesLock) {
            return new LinkedHashSet<>(cacheNames);
        }
    }

    /**
     * 把缓存名登记进内部集合；已登记时是空操作。
     */
    private void rememberCacheName(String cacheName) {
        synchronized (namesLock) {
            cacheNames.add(cacheName);
        }
    }

    /**
     * 从内部集合精确移除指定缓存名；没有该名称时是空操作。
     */
    private void forgetCacheName(String cacheName) {
        synchronized (namesLock) {
            cacheNames.remove(cacheName);
        }
    }
}
