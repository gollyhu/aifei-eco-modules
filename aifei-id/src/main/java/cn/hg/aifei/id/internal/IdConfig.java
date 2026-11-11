/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

import cn.hg.aifei.id.IdException;
import java.util.Properties;

/**
 * 配置域：配置键名与默认值的唯一定义点，也是 {@code id.*} 解析与校验的唯一实现点。
 *
 * <p>{@link #parse(Properties)} 在一次调用里完成解析与校验，只校验<b>与生效策略相关</b>的键：雪花策略下
 * 不读不校验号段键，号段策略下不读不校验 workerId 键（避免切换策略时残留键导致启动失败）。无关键对应的
 * 取值一律为默认值。</p>
 *
 * <p>取值对象的字段只读，{@link #parse(Properties)} 返回后不可变，可安全跨线程共享；解析失败抛
 * {@link IdException}，消息含键名与原值。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class IdConfig {

    /**
     * 模块命名前缀：全部配置键与模块锁名共用，{@code id.} 字面量的唯一定义点。
     */
    public static final String KEY_PREFIX = "id.";

    /**
     * 策略键。
     */
    public static final String KEY_STRATEGY = "id.strategy";

    /**
     * workerId 来源键，仅雪花策略生效。
     */
    public static final String KEY_WORKER_SOURCE = "id.worker.source";

    /**
     * 静态 workerId 键，仅雪花策略且来源为 {@code config} 生效。
     */
    public static final String KEY_WORKER_ID = "id.workerId";

    /**
     * workerId 租约时长键，仅雪花策略且来源为 {@code lock} 生效。
     */
    public static final String KEY_WORKER_LEASE_MILLIS = "id.worker.leaseMillis";

    /**
     * 号段长度键，仅号段策略生效。
     */
    public static final String KEY_SEGMENT_STEP = "id.segment.step";

    /**
     * 号段键名，仅号段策略生效。
     */
    public static final String KEY_SEGMENT_KEY = "id.segment.key";

    /**
     * 号段存储类型键，仅号段策略生效。
     */
    public static final String KEY_SEGMENT_STORE = "id.segment.store";

    /**
     * 状态有效期键：号段取段续期与 cache 模式 workerId 计数共用。
     */
    public static final String KEY_STATE_TTL_MILLIS = "id.state.ttlMillis";

    /**
     * 策略取值：雪花。
     */
    public static final String STRATEGY_SNOWFLAKE = "snowflake";

    /**
     * 策略取值：号段。
     */
    public static final String STRATEGY_SEGMENT = "segment";

    /**
     * workerId 来源取值：静态配置（默认）。
     */
    public static final String WORKER_SOURCE_CONFIG = "config";

    /**
     * workerId 来源取值：共享计数器取模。
     */
    public static final String WORKER_SOURCE_CACHE = "cache";

    /**
     * workerId 来源取值：锁租约抢占。
     */
    public static final String WORKER_SOURCE_LOCK = "lock";

    /**
     * 号段存储取值：共享缓存（默认）。
     */
    public static final String SEGMENT_STORE_CACHE = "cache";

    /**
     * 号段存储取值：进程内本地退化。
     */
    public static final String SEGMENT_STORE_LOCAL = "local";

    /**
     * 策略默认值。
     */
    public static final String DEFAULT_STRATEGY = STRATEGY_SNOWFLAKE;

    /**
     * workerId 来源默认值。
     */
    public static final String DEFAULT_WORKER_SOURCE = WORKER_SOURCE_CONFIG;

    /**
     * workerId 租约默认值（毫秒）。
     */
    public static final long DEFAULT_WORKER_LEASE_MILLIS = 30000L;

    /**
     * workerId 租约下界（毫秒）。
     */
    public static final long MIN_WORKER_LEASE_MILLIS = 1000L;

    /**
     * 号段长度默认值。
     */
    public static final int DEFAULT_SEGMENT_STEP = 1000;

    /**
     * 号段长度下界。
     */
    public static final int MIN_SEGMENT_STEP = 1;

    /**
     * 号段长度上界。
     */
    public static final int MAX_SEGMENT_STEP = 1000000;

    /**
     * 号段键名默认值。
     */
    public static final String DEFAULT_SEGMENT_KEY = "default";

    /**
     * 号段键名长度上限。
     */
    public static final int MAX_SEGMENT_KEY_LENGTH = 64;

    /**
     * 号段存储默认值。
     */
    public static final String DEFAULT_SEGMENT_STORE = SEGMENT_STORE_CACHE;

    /**
     * 状态有效期默认值：365 天（毫秒）。
     */
    public static final long DEFAULT_STATE_TTL_MILLIS = 31536000000L;

    /**
     * 状态有效期下界（毫秒）。
     */
    public static final long MIN_STATE_TTL_MILLIS = 60000L;

    /**
     * 状态有效期上界（毫秒），等于 {@code aifei-cache} 允许的上限（{@link Integer#MAX_VALUE} 秒）。
     */
    public static final long MAX_STATE_TTL_MILLIS = 2147483647000L;

    /**
     * 生效策略。
     */
    private final String strategy;

    /**
     * workerId 来源；号段策略下恒为 {@link #DEFAULT_WORKER_SOURCE}。
     */
    private final String workerSource;

    /**
     * 静态 workerId；未生效时为 {@link IdNames#MIN_WORKER_ID}。
     */
    private final int workerId;

    /**
     * workerId 租约时长（毫秒）；未生效时为 {@link #DEFAULT_WORKER_LEASE_MILLIS}。
     */
    private final long workerLeaseMillis;

    /**
     * 号段长度；未生效时为 {@link #DEFAULT_SEGMENT_STEP}。
     */
    private final int segmentStep;

    /**
     * 号段键名；未生效时为 {@link #DEFAULT_SEGMENT_KEY}。
     */
    private final String segmentKey;

    /**
     * 号段存储类型；未生效时为 {@link #DEFAULT_SEGMENT_STORE}。
     */
    private final String segmentStore;

    /**
     * 状态有效期（毫秒）；未生效时为 {@link #DEFAULT_STATE_TTL_MILLIS}。
     */
    private final long stateTtlMillis;

    /**
     * 由 {@link #parse(Properties)} 构造不可变配置。
     *
     * @param strategy 生效策略
     * @param workerSource workerId 来源
     * @param workerId 静态 workerId
     * @param workerLeaseMillis workerId 租约时长（毫秒）
     * @param segmentStep 号段长度
     * @param segmentKey 号段键名
     * @param segmentStore 号段存储类型
     * @param stateTtlMillis 状态有效期（毫秒）
     */
    private IdConfig(String strategy, String workerSource, int workerId, long workerLeaseMillis,
                     int segmentStep, String segmentKey, String segmentStore, long stateTtlMillis) {
        this.strategy = strategy;
        this.workerSource = workerSource;
        this.workerId = workerId;
        this.workerLeaseMillis = workerLeaseMillis;
        this.segmentStep = segmentStep;
        this.segmentKey = segmentKey;
        this.segmentStore = segmentStore;
        this.stateTtlMillis = stateTtlMillis;
    }

    /**
     * 解析并校验 {@code id.*} 配置：只校验与生效策略相关的键，无关键取默认值。
     *
     * @param properties 应用配置；为 {@code null} 时按全部键缺失处理
     * @return 不可变配置对象
     * @throws IdException 枚举取值非法、数值非整数、越界、必填缺失或号段键名非法
     */
    public static IdConfig parse(Properties properties) {
        Properties source = properties == null ? new Properties() : properties;
        String strategy = readEnum(source, KEY_STRATEGY, DEFAULT_STRATEGY,
                STRATEGY_SNOWFLAKE, STRATEGY_SEGMENT);
        if (STRATEGY_SEGMENT.equals(strategy)) {
            return parseSegment(source, strategy);
        }
        return parseSnowflake(source, strategy);
    }

    /**
     * 返回生效策略，取值 {@link #STRATEGY_SNOWFLAKE} 或 {@link #STRATEGY_SEGMENT}。
     *
     * @return 生效策略
     */
    public String strategy() {
        return strategy;
    }

    /**
     * 返回 workerId 来源，取值 {@code config} / {@code cache} / {@code lock}。
     *
     * <p>仅雪花策略生效；号段策略下恒返回 {@link #DEFAULT_WORKER_SOURCE}，该键不被读取与校验。</p>
     *
     * @return workerId 来源
     */
    public String workerSource() {
        return workerSource;
    }

    /**
     * 返回静态配置的 workerId。
     *
     * <p>仅雪花策略且来源为 {@code config} 生效；其它情况恒返回 {@link IdNames#MIN_WORKER_ID}，
     * 该键不被读取与校验。</p>
     *
     * @return workerId，生效时保证落在 {@code 0..1023}
     */
    public int workerId() {
        return workerId;
    }

    /**
     * 返回 workerId 租约时长（毫秒）。
     *
     * <p>仅雪花策略且来源为 {@code lock} 生效；其它情况恒返回默认值，该键不被读取与校验。</p>
     *
     * @return 租约时长（毫秒），生效时保证不小于 {@link #MIN_WORKER_LEASE_MILLIS}
     */
    public long workerLeaseMillis() {
        return workerLeaseMillis;
    }

    /**
     * 返回号段长度。
     *
     * <p>仅号段策略生效；雪花策略下恒返回默认值，该键不被读取与校验。</p>
     *
     * @return 号段长度，生效时保证落在 {@code [1, 1000000]}
     */
    public int segmentStep() {
        return segmentStep;
    }

    /**
     * 返回号段键名。
     *
     * <p>仅号段策略生效；雪花策略下恒返回默认值，该键不被读取与校验。</p>
     *
     * @return 号段键名，生效时保证非空白、无空白字符且长度不超过 {@link #MAX_SEGMENT_KEY_LENGTH}
     */
    public String segmentKey() {
        return segmentKey;
    }

    /**
     * 返回号段存储类型，取值 {@code cache} 或 {@code local}。
     *
     * <p>仅号段策略生效；雪花策略下恒返回默认值，该键不被读取与校验。</p>
     *
     * @return 号段存储类型
     */
    public String segmentStore() {
        return segmentStore;
    }

    /**
     * 返回状态有效期（毫秒），用于号段取段续期与 cache 模式 workerId 计数。
     *
     * <p>仅在「号段策略」或「雪花策略且来源为 {@code cache}」时读取与校验；其它情况恒返回默认值。</p>
     *
     * @return 状态有效期（毫秒），生效时保证落在 {@code [60000, 2147483647000]}
     */
    public long stateTtlMillis() {
        return stateTtlMillis;
    }

    // ─────────── 分策略解析 ───────────

    /**
     * 解析雪花策略的配置：workerId 来源、静态 workerId、租约时长、状态有效期。
     *
     * @param properties 应用配置
     * @param strategy 已确定的策略
     * @return 不可变配置对象
     */
    private static IdConfig parseSnowflake(Properties properties, String strategy) {
        String workerSource = readEnum(properties, KEY_WORKER_SOURCE, DEFAULT_WORKER_SOURCE,
                WORKER_SOURCE_CONFIG, WORKER_SOURCE_CACHE, WORKER_SOURCE_LOCK);
        int workerId = IdNames.MIN_WORKER_ID;
        long workerLeaseMillis = DEFAULT_WORKER_LEASE_MILLIS;
        long stateTtlMillis = DEFAULT_STATE_TTL_MILLIS;
        if (WORKER_SOURCE_CONFIG.equals(workerSource)) {
            workerId = readWorkerId(properties);
        } else if (WORKER_SOURCE_CACHE.equals(workerSource)) {
            stateTtlMillis = readStateTtl(properties);
        } else {
            workerLeaseMillis = readWorkerLease(properties);
        }
        return new IdConfig(strategy, workerSource, workerId, workerLeaseMillis, DEFAULT_SEGMENT_STEP,
                DEFAULT_SEGMENT_KEY, DEFAULT_SEGMENT_STORE, stateTtlMillis);
    }

    /**
     * 解析号段策略的配置：号段长度、键名、存储类型、状态有效期。
     *
     * @param properties 应用配置
     * @param strategy 已确定的策略
     * @return 不可变配置对象
     */
    private static IdConfig parseSegment(Properties properties, String strategy) {
        int segmentStep = readIntInRange(properties, KEY_SEGMENT_STEP, DEFAULT_SEGMENT_STEP,
                MIN_SEGMENT_STEP, MAX_SEGMENT_STEP);
        String segmentKey = readSegmentKey(properties);
        String segmentStore = readEnum(properties, KEY_SEGMENT_STORE, DEFAULT_SEGMENT_STORE,
                SEGMENT_STORE_CACHE, SEGMENT_STORE_LOCAL);
        long stateTtlMillis = readStateTtl(properties);
        return new IdConfig(strategy, DEFAULT_WORKER_SOURCE, IdNames.MIN_WORKER_ID,
                DEFAULT_WORKER_LEASE_MILLIS, segmentStep, segmentKey, segmentStore, stateTtlMillis);
    }

    // ─────────── 单项读取与校验 ───────────

    /**
     * 读取 {@code id.workerId}：来源为 {@code config} 时必填，取值须落在 workerId 范围内。
     *
     * @param properties 应用配置
     * @return 合法的 workerId
     */
    private static int readWorkerId(Properties properties) {
        String raw = trimmed(properties, KEY_WORKER_ID);
        if (raw == null) {
            throw IdErrors.configRequired(KEY_WORKER_ID, KEY_WORKER_SOURCE + "=" + WORKER_SOURCE_CONFIG);
        }
        int workerId = parseInt(KEY_WORKER_ID, raw);
        if (workerId < IdNames.MIN_WORKER_ID || workerId > IdNames.MAX_WORKER_ID) {
            throw IdErrors.configOutOfRange(KEY_WORKER_ID, raw,
                    IdNames.MIN_WORKER_ID, IdNames.MAX_WORKER_ID);
        }
        return workerId;
    }

    /**
     * 读取 {@code id.worker.leaseMillis}：须不小于租约下界。
     *
     * @param properties 应用配置
     * @return 合法的租约时长（毫秒）
     */
    private static long readWorkerLease(Properties properties) {
        String raw = trimmed(properties, KEY_WORKER_LEASE_MILLIS);
        if (raw == null) {
            return DEFAULT_WORKER_LEASE_MILLIS;
        }
        long lease = parseLong(KEY_WORKER_LEASE_MILLIS, raw);
        if (lease < MIN_WORKER_LEASE_MILLIS) {
            throw IdErrors.configBelowMinimum(KEY_WORKER_LEASE_MILLIS, raw, MIN_WORKER_LEASE_MILLIS);
        }
        return lease;
    }

    /**
     * 读取 {@code id.state.ttlMillis}：须落在有效期范围内。
     *
     * @param properties 应用配置
     * @return 合法的状态有效期（毫秒）
     */
    private static long readStateTtl(Properties properties) {
        return readLongInRange(properties, KEY_STATE_TTL_MILLIS, DEFAULT_STATE_TTL_MILLIS,
                MIN_STATE_TTL_MILLIS, MAX_STATE_TTL_MILLIS);
    }

    /**
     * 读取 {@code id.segment.key}：须非空白、不含空白字符且不超长。
     *
     * @param properties 应用配置
     * @return 合法的号段键名
     */
    private static String readSegmentKey(Properties properties) {
        String raw = trimmed(properties, KEY_SEGMENT_KEY);
        if (raw == null) {
            return DEFAULT_SEGMENT_KEY;
        }
        if (raw.length() > MAX_SEGMENT_KEY_LENGTH) {
            throw IdErrors.configInvalidSegmentKey(KEY_SEGMENT_KEY, raw, MAX_SEGMENT_KEY_LENGTH);
        }
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isWhitespace(raw.charAt(i))) {
                throw IdErrors.configInvalidSegmentKey(KEY_SEGMENT_KEY, raw, MAX_SEGMENT_KEY_LENGTH);
            }
        }
        return raw;
    }

    /**
     * 读取枚举型配置：缺省取默认值，取值须落在允许集合内。
     *
     * @param properties 应用配置
     * @param key 配置键名
     * @param defaultValue 缺省值
     * @param candidates 允许取值
     * @return 合法的取值
     */
    private static String readEnum(Properties properties, String key, String defaultValue,
                                   String... candidates) {
        String raw = trimmed(properties, key);
        if (raw == null) {
            return defaultValue;
        }
        for (String candidate : candidates) {
            if (candidate.equals(raw)) {
                return raw;
            }
        }
        StringBuilder display = new StringBuilder();
        for (int i = 0; i < candidates.length; i++) {
            if (i > 0) {
                display.append(", ");
            }
            display.append(candidates[i]);
        }
        throw IdErrors.configNotInEnum(key, raw, display.toString());
    }

    /**
     * 读取整型配置：缺省取默认值，取值须落在闭区间内。
     *
     * @param properties 应用配置
     * @param key 配置键名
     * @param defaultValue 缺省值
     * @param min 下界（含）
     * @param max 上界（含）
     * @return 合法的取值
     */
    private static int readIntInRange(Properties properties, String key, int defaultValue, int min, int max) {
        String raw = trimmed(properties, key);
        if (raw == null) {
            return defaultValue;
        }
        int value = parseInt(key, raw);
        if (value < min || value > max) {
            throw IdErrors.configOutOfRange(key, raw, min, max);
        }
        return value;
    }

    /**
     * 读取长整型配置：缺省取默认值，取值须落在闭区间内。
     *
     * @param properties 应用配置
     * @param key 配置键名
     * @param defaultValue 缺省值
     * @param min 下界（含）
     * @param max 上界（含）
     * @return 合法的取值
     */
    private static long readLongInRange(Properties properties, String key, long defaultValue,
                                        long min, long max) {
        String raw = trimmed(properties, key);
        if (raw == null) {
            return defaultValue;
        }
        long value = parseLong(key, raw);
        if (value < min || value > max) {
            throw IdErrors.configOutOfRange(key, raw, min, max);
        }
        return value;
    }

    /**
     * 取出配置值并去掉首尾空白；缺失、空白一律视为未配置。
     *
     * @param properties 应用配置
     * @param key 配置键名
     * @return 去空白后的取值；未配置时 {@code null}
     */
    private static String trimmed(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        return candidate.isEmpty() ? null : candidate;
    }

    /**
     * 解析整型取值，失败时抛携带键名与原值的异常。
     *
     * @param key 配置键名
     * @param raw 配置原值
     * @return 解析结果
     * @throws IdException 取值不是整数
     */
    private static int parseInt(String key, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw IdErrors.configNotInteger(key, raw);
        }
    }

    /**
     * 解析长整型取值，失败时抛携带键名与原值的异常。
     *
     * @param key 配置键名
     * @param raw 配置原值
     * @return 解析结果
     * @throws IdException 取值不是整数
     */
    private static long parseLong(String key, String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw IdErrors.configNotInteger(key, raw);
        }
    }
}
