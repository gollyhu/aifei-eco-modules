/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.redis;

import cn.hg.aifei.cache.internal.AbstractCounter;
import cn.hg.aifei.cache.internal.CacheValidator;
import cn.hg.aifei.cache.internal.Config;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 基于 Redis 的分布式计数实现。
 */
public class RedisCounter extends AbstractCounter {

    /**
     * 每轮 SCAN 的工作量提示，不保证每页返回数量；与 {@code RedisCache} 同值（本后端的 SCAN
     * 分页提示，不进入存储，故不强制收敛到单一定义点）。
     */
    private static final int SCAN_COUNT = 1_000;

    /*
     * Redis Lua 会把嵌套命令的 integer reply 转为 Lua number，大整数直接返回可能丢精度。
     * 因此脚本执行 INCRBY 后读取 Redis 原始字符串作为返回值。
     *
     * 计数项首次创建（PTTL == -2）时顺带把计数名称 SADD 进 KEYS[2]，与数值创建同处一段脚本，
     * 不额外增加一次往返；已存在的计数项不会重复登记。
     *
     * 登记只发生在这里：本类没有对应的 Java 登记方法（同源的 CaffeineCounter 有），以保证登记与计数项
     * 创建同处一次脚本。拆成「先建值、再登记」两步会退回成两条可被中断的命令。
     */
    private static final String UPDATE_SCRIPT =
            "local ttl = redis.call('pttl', KEYS[1]); "
                    + "if ttl == -2 then "
                    + "redis.call('psetex', KEYS[1], ARGV[2], '0'); "
                    + "redis.call('sadd', KEYS[2], ARGV[4]); "
                    + "elseif ttl == -1 then "
                    + "return redis.error_reply('counter value must have ttl'); "
                    + "end; "
                    + "redis.call('incrby', KEYS[1], ARGV[1]); "
                    + "if ARGV[3] == '1' then "
                    + "redis.call('pexpire', KEYS[1], ARGV[2]); "
                    + "end; "
                    + "return redis.call('get', KEYS[1]);";

    private final RedisClient client;

    /**
     * 使用指定 Redis 客户端、命名空间与实例默认 TTL 创建计数器。
     *
     * @param client Redis 客户端（与同源缓存共享）
     * @param namespace 命名空间
     * @param ttlMillis 实例默认 TTL，单位毫秒
     */
    RedisCounter(RedisClient client, String namespace, long ttlMillis) {
        super(namespace, ttlMillis);
        this.client = Objects.requireNonNull(client, "client can not be null");
        Objects.requireNonNull(namespace, "namespace can not be null");
    }

    /**
     * 从 Redis 读取计数值。
     *
     * @throws IllegalStateException 计数值不是合法整数文本，或计数项没有 TTL
     */
    @Override
    public Long get(String counterName, String key) {
        if (CacheValidator.isInvalidCounterNameAndKey(counterName, key)) {
            return null;
        }
        String redisKey = counterKey(counterName, key);
        String value = client.get(redisKey);
        if (value == null) {
            return null;
        }
        long ttlMillis = client.pttl(redisKey);
        if (ttlMillis == -2L) {
            return null;
        }
        if (ttlMillis == -1L) {
            throw new IllegalStateException("counter value must have ttl");
        }
        return parseCounterValue(value);
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
        client.del(counterKey(counterName, key));
    }

    /**
     * 清空指定计数名称下的计数项，并把该计数名称从 {@link #counterNames()} 中移除。
     */
    @Override
    public void clear(String counterName) {
        String validCounterName = CacheValidator.requireCounterName(counterName);
        byte[] pattern = counterPattern(validCounterName);
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
        forgetCounterName(validCounterName);
    }

    /**
     * 返回曾被创建过的计数名称集合；与同一 Redis 上的其它实例共享。
     */
    @Override
    public Set<String> counterNames() {
        return new LinkedHashSet<>(client.smembers(counterNamesKey()));
    }

    /**
     * 用一段 Lua 脚本把「缺失初始化 + TTL 设置 + INCRBY（+ 计数名称登记）」合成一次原子操作。
     *
     * <p>TTL 先在 Java 侧校验再执行脚本：超限值一旦进入 Redis，{@code INCRBY} 已生效而
     * {@code PEXPIRE} 报错，会留下「数值被改动、TTL 未刷新」的脏状态。</p>
     */
    private long updateCounter(String counterName, String key, long step, Duration ttl,
                               boolean increase, boolean refreshTtl) {
        String validCounterName = CacheValidator.requireCounterName(counterName);
        String validKey = CacheValidator.requireKey(key);
        long validStep = CacheValidator.requireCounterStep(step);
        long ttlMillis = CacheValidator.requireTtl(ttl);
        long delta = increase ? validStep : Math.negateExact(validStep);

        try {
            Object result = client.eval(
                    UPDATE_SCRIPT,
                    Arrays.asList(counterKey(validCounterName, validKey), counterNamesKey()),
                    Arrays.asList(Long.toString(delta), Long.toString(ttlMillis), refreshTtl ? "1" : "0",
                            validCounterName)
            );
            return toLong(result);
        } catch (JedisDataException e) {
            throw translateCounterException(e);
        }
    }

    private String counterKey(String counterName, String key) {
        return physicalKey(namespace, counterName, key);
    }

    /**
     * 从内部集合精确移除指定计数名称；没有该名称时是空操作。
     */
    private void forgetCounterName(String counterName) {
        client.srem(counterNamesKey(), counterName);
    }

    /**
     * 生成 {@code counterNames()} 内部集合的固定 key；必须包含命名空间。
     */
    private String counterNamesKey() {
        return namespace + ":" + Config.COUNTER_NAMES_NAME;
    }

    /**
     * 生成清理计数时使用的 Redis glob 匹配模式。
     *
     * <p>对 {@code namespace + ":" + Config.COUNTER_KEY_PREFIX + counterName} 整体转义；冒号不是 glob
     * 特殊字符，无需转义。名称集合 key 不含 {@code Config.COUNTER_KEY_PREFIX}，不会被本模式命中。</p>
     */
    private byte[] counterPattern(String counterName) {
        return bytes(escapeRedisGlob(namespace + ":" + Config.COUNTER_KEY_PREFIX + counterName) + ":*");
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

    private static long toLong(Object result) {
        if (result instanceof Long) {
            return (Long) result;
        }
        return Long.parseLong(String.valueOf(result));
    }

    /**
     * 解析 Redis 原生 signed long 文本。
     */
    private static Long parseCounterValue(String value) {
        if (!isSignedLongText(value)) {
            throw new IllegalStateException("counter value must be Redis integer with ttl");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("counter value must be Redis integer with ttl", e);
        }
    }

    /**
     * 判断字符串是否为 Redis 计数值格式。
     */
    private static boolean isSignedLongText(String value) {
        if (value.isEmpty()) {
            return false;
        }
        int index = value.charAt(0) == '-' ? 1 : 0;
        if (index == value.length()) {
            return false;
        }
        while (index < value.length()) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
            index++;
        }
        return true;
    }

    /**
     * 将 Redis integer 错误转换为接口层更稳定的异常。
     */
    private static RuntimeException translateCounterException(JedisDataException e) {
        String message = e.getMessage();
        if (message != null && message.contains("overflow")) {
            ArithmeticException overflow = new ArithmeticException(message);
            overflow.initCause(e);
            return overflow;
        }
        if (message != null && (message.contains("not an integer")
                || message.contains("out of range")
                || message.contains("counter value must have ttl"))) {
            return new IllegalStateException("counter value must be Redis integer with ttl", e);
        }
        return e;
    }
}
