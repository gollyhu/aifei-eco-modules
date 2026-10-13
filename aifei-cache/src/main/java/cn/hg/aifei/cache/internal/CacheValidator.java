/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.internal;

import cn.aifei.util.StrUtil;
import cn.hg.aifei.cache.CacheKeyMatchType;
import java.time.Duration;

/**
 * 提供缓存接口参数与命名空间的共用校验。
 */
public final class CacheValidator {

    /**
     * 名字与命名空间中不得出现的分段符：它同时是物理 key 的分段符，进入名字或命名空间会让层级歧义。
     */
    private static final char NAME_SEPARATOR = ':';

    /**
     * 禁止创建工具类实例。
     */
    private CacheValidator() {
    }

    /**
     * 判断缓存名与缓存键是否有效。
     */
    public static boolean isValidCacheNameAndKey(String cacheName, String key) {
        return isValidName(cacheName) && !StrUtil.isBlank(key);
    }

    /**
     * 判断计数名称与计数键是否无效：两者任一无效（空白或含分段符）即无效。
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @return 任一无效时 {@code true}
     */
    public static boolean isInvalidCounterNameAndKey(String counterName, String key) {
        return !isValidName(counterName) || StrUtil.isBlank(key);
    }

    /**
     * 校验缓存名非空白且不含分段符。
     */
    public static String requireCacheName(String cacheName) {
        if (StrUtil.isBlank(cacheName)) {
            throw new IllegalArgumentException("cacheName can not be blank");
        }
        if (cacheName.indexOf(NAME_SEPARATOR) >= 0) {
            throw new IllegalArgumentException("cacheName must not contain ':'");
        }
        return cacheName;
    }

    /**
     * 校验计数名称非空白且不含分段符。
     */
    public static String requireCounterName(String counterName) {
        if (StrUtil.isBlank(counterName)) {
            throw new IllegalArgumentException("counterName can not be blank");
        }
        if (counterName.indexOf(NAME_SEPARATOR) >= 0) {
            throw new IllegalArgumentException("counterName must not contain ':'");
        }
        return counterName;
    }

    /**
     * 校验命名空间非空白且不含分段符。
     *
     * <p>命名空间是物理 key 的第一段：它含分段符时 {@code namespace + ":" + cacheName} 的拼接不再单射，
     * 两个不同实例可落到同一物理 key，而启动期排他判定按「唯一名 + 命名空间」判重、不会报冲突。校验
     * 发生在配置解析期（启动即失败），不存在名字那种「读静默 / 写抛异常」的两侧差异。</p>
     */
    public static String requireNamespace(String namespace) {
        if (StrUtil.isBlank(namespace)) {
            throw new IllegalArgumentException("namespace can not be blank");
        }
        if (namespace.indexOf(NAME_SEPARATOR) >= 0) {
            throw new IllegalArgumentException("namespace must not contain ':'");
        }
        return namespace;
    }

    /**
     * 判断名字是否有效：非空白且不含分段符。写路径据此抛异常、读路径据此按未命中处理，两侧共用同一判定。
     */
    private static boolean isValidName(String name) {
        return !StrUtil.isBlank(name) && name.indexOf(NAME_SEPARATOR) < 0;
    }

    /**
     * 校验缓存键非空白。
     */
    public static String requireKey(String key) {
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("key can not be blank");
        }
        return key;
    }

    /**
     * 校验键分页的偏移量不小于 0。
     */
    public static int requireOffset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        return offset;
    }

    /**
     * 校验键分页的条数上限大于 0。
     */
    public static int requireLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        return limit;
    }

    /**
     * 校验键匹配类型不为 {@code null}。
     */
    public static CacheKeyMatchType requireMatchType(CacheKeyMatchType matchType) {
        if (matchType == null) {
            throw new IllegalArgumentException("matchType can not be null");
        }
        return matchType;
    }

    /**
     * 校验有效期并返回毫秒值；不得小于 1 毫秒，也不得超过 {@link Integer#MAX_VALUE} 秒。
     */
    public static long requireTtl(Duration ttl) {
        if (ttl == null) {
            throw new IllegalArgumentException("ttl can not be null");
        }
        long seconds = ttl.getSeconds();
        int nanos = ttl.getNano();
        if (seconds < 0 || (seconds == 0 && nanos < 1_000_000)) {
            throw new IllegalArgumentException("ttl must be at least one millisecond");
        }
        if (seconds > Integer.MAX_VALUE || (seconds == Integer.MAX_VALUE && nanos > 0)) {
            throw new IllegalArgumentException("ttl must not exceed " + Integer.MAX_VALUE + " seconds");
        }
        // 秒数已限制在 [0, Integer.MAX_VALUE] 内，乘以 1000 不会溢出。
        return seconds * 1000L + nanos / 1_000_000;
    }

    /**
     * 校验计数步长大于零。
     */
    public static long requireCounterStep(long step) {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be greater than zero");
        }
        return step;
    }

    /**
     * 校验缓存值不为 {@code null}。
     *
     * <p>保留自上游的校验入口，当前无调用方：写路径不使用它 —— 写入 {@code null} 按
     * {@code cacheNull} 规则处理，不抛异常。</p>
     */
    public static void requireValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value can not be null");
        }
    }
}
