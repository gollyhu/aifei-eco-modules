/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.support;

import cn.hg.aifei.cache.Counter;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 单测用计数器：只实现「原子增加并续期」，其余方法一律抛 {@link UnsupportedOperationException}。
 *
 * <p>行为刻意收窄：本模块只通过 {@code Counter#increaseAndRefreshTtl} 取号段末端，其它入口一旦被误用，
 * 用例会立刻以 {@code UnsupportedOperationException} 失败，而不是被桩的宽容实现掩盖。同时记录最近一次
 * 调用的名称 / 键 / 步长 / 有效期，用于断言「分区名、段键拼装、步长与 TTL 如实下传」。</p>
 *
 * <p>线程安全：全部方法 {@code synchronized}；内部按「名称 + 键」累计，模拟计数器的原子自增语义。</p>
 */
public final class FakeCounter implements Counter {

    /**
     * 计数值，键为「名称 + {@code /} + 键」。
     */
    private final Map<String, Long> counters = new LinkedHashMap<>();

    /**
     * 注入的故障；非 {@code null} 时直接抛出。
     */
    private RuntimeException failure;

    /**
     * 最近一次调用的计数名称。
     */
    private String lastCounterName;

    /**
     * 最近一次调用的键。
     */
    private String lastKey;

    /**
     * 最近一次调用的步长。
     */
    private long lastStep;

    /**
     * 最近一次调用的有效期。
     */
    private Duration lastTtl;

    /**
     * 注入一次故障，模拟共享后端不可用。
     *
     * @param failure 抛出的运行时异常
     * @return 本对象，便于链式构造
     */
    public FakeCounter failWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    /**
     * 原子增加计数值并记录调用参数。
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @param step 步长
     * @param ttl 每次命中的新有效期
     * @return 更新后的计数值
     */
    @Override
    public synchronized long increaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
        lastCounterName = counterName;
        lastKey = key;
        lastStep = step;
        lastTtl = ttl;
        if (failure != null) {
            throw failure;
        }
        String compositeKey = counterName + "/" + key;
        Long previous = counters.get(compositeKey);
        long updated = (previous == null ? 0L : previous.longValue()) + step;
        counters.put(compositeKey, Long.valueOf(updated));
        return updated;
    }

    /**
     * 读取指定计数项的当前值。
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @return 当前值；不存在时为 {@code 0}
     */
    public synchronized long valueOf(String counterName, String key) {
        Long value = counters.get(counterName + "/" + key);
        return value == null ? 0L : value.longValue();
    }

    /**
     * 返回最近一次调用的计数名称。
     *
     * @return 计数名称；尚未调用时为 {@code null}
     */
    public String lastCounterName() {
        return lastCounterName;
    }

    /**
     * 返回最近一次调用的键。
     *
     * @return 键；尚未调用时为 {@code null}
     */
    public String lastKey() {
        return lastKey;
    }

    /**
     * 返回最近一次调用的步长。
     *
     * @return 步长；尚未调用时为 {@code 0}
     */
    public long lastStep() {
        return lastStep;
    }

    /**
     * 返回最近一次调用的有效期。
     *
     * @return 有效期；尚未调用时为 {@code null}
     */
    public Duration lastTtl() {
        return lastTtl;
    }

    /**
     * 本模块不使用该入口。
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @return 不会返回
     */
    @Override
    public Long get(String counterName, String key) {
        throw new UnsupportedOperationException("aifei-id 不读取计数值");
    }

    /**
     * 本模块不使用该入口。
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @param step 步长
     * @param ttl 首次创建时的有效期
     * @return 不会返回
     */
    @Override
    public long increase(String counterName, String key, long step, Duration ttl) {
        throw new UnsupportedOperationException("aifei-id 只用 increaseAndRefreshTtl");
    }

    /**
     * 本模块不使用该入口。
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @param step 步长
     * @param ttl 首次创建时的有效期
     * @return 不会返回
     */
    @Override
    public long decrease(String counterName, String key, long step, Duration ttl) {
        throw new UnsupportedOperationException("aifei-id 只做计数递增");
    }

    /**
     * 本模块不使用该入口。
     *
     * @param counterName 计数名称
     * @param key 计数键
     * @param step 步长
     * @param ttl 每次命中的新有效期
     * @return 不会返回
     */
    @Override
    public long decreaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
        throw new UnsupportedOperationException("aifei-id 只做计数递增");
    }

    /**
     * 本模块不使用该入口。
     *
     * @param counterName 计数名称
     * @param key 计数键
     */
    @Override
    public void remove(String counterName, String key) {
        throw new UnsupportedOperationException("aifei-id 不删除计数项");
    }

    /**
     * 本模块不使用该入口。
     *
     * @param counterName 计数名称
     */
    @Override
    public void clear(String counterName) {
        throw new UnsupportedOperationException("aifei-id 不清空计数项");
    }

    /**
     * 本模块不使用该入口。
     *
     * @return 不会返回
     */
    @Override
    public Set<String> counterNames() {
        throw new UnsupportedOperationException("aifei-id 不枚举计数名称");
    }

    /**
     * 本模块不使用该入口：TTL 一律显式传入，不依赖实例默认值。
     *
     * @return 不会返回
     */
    @Override
    public Duration ttl() {
        throw new UnsupportedOperationException("aifei-id 的 TTL 来自配置，不读取实例默认值");
    }

    /**
     * 本模块不使用该入口。
     *
     * @return 不会返回
     */
    @Override
    public String namespace() {
        throw new UnsupportedOperationException("aifei-id 只给逻辑键，物理前缀由缓存实例负责");
    }
}
