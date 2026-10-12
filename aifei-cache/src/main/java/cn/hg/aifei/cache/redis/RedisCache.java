/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.redis;

import cn.hg.aifei.cache.Counter;
import cn.hg.aifei.cache.internal.*;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 基于 Redis 的分布式缓存实现。
 */
public class RedisCache extends AbstractCache implements AutoCloseable, CounterFactory, UniqueNameAware {

    /**
     * 每轮 SCAN 的工作量提示，不保证每页返回数量；与 {@code RedisCounter} 同值（本后端的 SCAN
     * 分页提示，不进入存储，故不强制收敛到单一定义点）。
     */
    private static final int SCAN_COUNT = 1_000;

    private final RedisClient client;

    private final RedisValueCodec codec;

    /**
     * 唯一名（{@code host:port/database}），构造期算好并固定；本类不保留 {@link RedisConfig}。
     */
    private final String uniqueName;

    private volatile boolean closed;

    /**
     * 使用默认实例级配置连接 Redis。
     *
     * @param config Redis 连接配置
     */
    public RedisCache(RedisConfig config) {
        this(config, Config.DEFAULT_NAMESPACE, Config.DEFAULT_TTL, false);
    }

    /**
     * 使用指定实例级配置连接 Redis。
     *
     * @param config Redis 连接配置
     * @param namespace 命名空间
     * @param ttlMillis 默认 TTL，单位毫秒
     * @param cacheNull 是否缓存 {@code null} 值
     */
    public RedisCache(RedisConfig config, String namespace, long ttlMillis, boolean cacheNull) {
        super(namespace, ttlMillis, cacheNull);
        RedisConfig redisConfig = Objects.requireNonNull(config, "config can not be null");
        CacheValidator.requireNamespace(namespace);
        this.codec = redisConfig.createValueCodec();
        this.client = redisConfig.createClient();
        this.uniqueName = redisConfig.uniqueName();
    }

    /**
     * 返回该实例所在 Redis 逻辑库的唯一名：{@code host:port/database}，供启动期排他判定使用。
     */
    @Override
    public String uniqueName() {
        return uniqueName;
    }

    /**
     * 创建共享相同 Redis 客户端与连接池、并继承本实例默认 TTL 的计数器。
     */
    @Override
    public Counter createCounter() {
        return new RedisCounter(client, namespace, ttl().toMillis());
    }

    /**
     * 从 Redis 读取缓存值，命中哨兵时返回 {@code null}。
     */
    @Override
    public <T> T get(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return null;
        }
        byte[] stored = client.get(bytes(physicalKey(namespace, cacheName, key)));
        return stored == null ? null : fromStoredValue(codec.deserialize(stored));
    }

    /**
     * 存储级判定缓存项是否存在且未过期。
     */
    @Override
    public boolean exists(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return false;
        }
        return client.exists(bytes(physicalKey(namespace, cacheName, key)));
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

        client.set(
                bytes(physicalKey(namespace, validCacheName, validKey)),
                codec.serialize(stored),
                SetParams.setParams().px(ttlMillis)
        );
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

        String result = client.set(
                bytes(physicalKey(namespace, validCacheName, validKey)),
                codec.serialize(stored),
                SetParams.setParams().px(ttlMillis).nx()
        );
        boolean written = result != null;
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
        return client.pexpire(bytes(physicalKey(namespace, validCacheName, validKey)), ttlMillis) == 1L;
    }

    /**
     * 删除单个缓存项。
     */
    @Override
    public void remove(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return;
        }
        client.del(bytes(physicalKey(namespace, cacheName, key)));
    }

    /**
     * 清空指定缓存名下的缓存项，并把该缓存名从 {@link #cacheNames()} 中移除。
     */
    @Override
    public void clear(String cacheName) {
        String validCacheName = CacheValidator.requireCacheName(cacheName);
        byte[] pattern = redisPattern(validCacheName);
        byte[] cursor = ScanParams.SCAN_POINTER_START_BINARY;
        ScanParams scanParams = new ScanParams().match(pattern).count(SCAN_COUNT);
        ScanResult<byte[]> page;
        do {
            page = client.scan(cursor, scanParams);
            List<byte[]> keys = page.getResult();
            if (!keys.isEmpty()) {
                client.unlink(keys.toArray(new byte[keys.size()][]));
            }
            cursor = page.getCursorAsBytes();
        } while (!page.isCompleteIteration());
        forgetCacheName(validCacheName);
    }

    /**
     * 返回缓存项的剩余有效期：不存在返回 {@code null}，无过期时间返回
     * {@link Integer#MAX_VALUE} 秒，其余返回剩余毫秒。
     */
    @Override
    public Duration getTtl(String cacheName, String key) {
        if (!CacheValidator.isValidCacheNameAndKey(cacheName, key)) {
            return null;
        }
        long ttl = client.pttl(bytes(physicalKey(namespace, cacheName, key)));
        if (ttl == -2L) {
            return null;
        }
        if (ttl == -1L) {
            return Duration.ofSeconds(Integer.MAX_VALUE);
        }
        return Duration.ofMillis(ttl);
    }

    /**
     * 返回该缓存名下指定区间的键，按字典序升序；切片语义见 {@link KeyRanges#slice}。
     */
    @Override
    public List<String> keys(String cacheName, int begin, int end) {
        String validCacheName = CacheValidator.requireCacheName(cacheName);
        String prefix = physicalKey(namespace, validCacheName, "");
        byte[] pattern = bytes(escapeRedisGlob(prefix) + "*");
        List<String> keys = new ArrayList<>();
        byte[] cursor = ScanParams.SCAN_POINTER_START_BINARY;
        ScanParams scanParams = new ScanParams().match(pattern).count(SCAN_COUNT);
        ScanResult<byte[]> page;
        do {
            page = client.scan(cursor, scanParams);
            for (byte[] physical : page.getResult()) {
                keys.add(new String(physical, StandardCharsets.UTF_8).substring(prefix.length()));
            }
            cursor = page.getCursorAsBytes();
        } while (!page.isCompleteIteration());
        Collections.sort(keys);
        return KeyRanges.slice(keys, begin, end);
    }

    /**
     * 返回已被写入过的缓存名集合；与同一 Redis 上的其它实例共享。
     */
    @Override
    public Set<String> cacheNames() {
        return new LinkedHashSet<>(client.smembers(cacheNamesKey()));
    }

    /**
     * 关闭 Redis 客户端；重复调用不会重复关闭。
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        client.close();
        closed = true;
    }

    /**
     * 把缓存名登记进内部集合；已登记时是空操作。
     */
    private void rememberCacheName(String cacheName) {
        client.sadd(cacheNamesKey(), cacheName);
    }

    /**
     * 生成 {@code cacheNames()} 内部集合的固定 key；必须包含命名空间。
     */
    private String cacheNamesKey() {
        return namespace + ":" + Config.CACHE_NAMES_NAME;
    }

    /**
     * 从内部集合精确移除指定缓存名；没有该名称时是空操作。
     */
    private void forgetCacheName(String cacheName) {
        client.srem(cacheNamesKey(), cacheName);
    }

    /**
     * 生成清理缓存时使用的 Redis glob 匹配模式。
     *
     * <p>对 {@code namespace + ":" + cacheName} 整体转义；冒号不是 glob 特殊字符，无需转义。</p>
     */
    private byte[] redisPattern(String cacheName) {
        return bytes(escapeRedisGlob(namespace + ":" + cacheName) + ":*");
    }

    /**
     * 转义 Redis glob 匹配模式中的特殊字符。
     */
    private static String escapeRedisGlob(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '*' || character == '?'
                    || character == '[' || character == ']' || character == '\\') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
