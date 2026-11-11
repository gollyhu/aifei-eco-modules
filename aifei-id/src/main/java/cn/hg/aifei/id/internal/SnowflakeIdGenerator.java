/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

import cn.hg.aifei.id.IdException;
import cn.hg.aifei.id.IdGenerator;
import java.util.function.LongSupplier;

/**
 * 雪花算法实现的唯一定义点：位数与移位、纪元、序列上限、回拨容差、时刻采样、等待逻辑都只在本类出现。
 *
 * <p><b>位布局（冻结数据格式）</b>：{@code [1 符号位=0][41 毫秒时间戳][10 workerId][12 序列号]}，自高向低；
 * 组合公式为 {@code ((now - EPOCH) << 22) | (workerId << 12) | sequence}，三段无重叠。单节点上限
 * 4096 个/毫秒，序列耗尽即等下一毫秒。纪元 {@code EPOCH = 2024-01-01T00:00:00Z}，41 位毫秒可覆盖到
 * 约 2093 年，越界即失败（不静默回绕）。</p>
 *
 * <p><b>回拨策略</b>：回拨不超过 {@code 5ms}（NTP 微调与 GC 抖动的量级）时在锁内自旋等待追上原时间戳，
 * 追上后沿用「同一毫秒」分支继续分配（序列号 +1，不重置，故结构上不重号）；超过 {@code 5ms} 的回拨
 * 几乎都是系统性时钟故障，直接失败。永不更换 workerId。</p>
 *
 * <p><b>线程安全</b>：{@link #nextId()} 为 {@code synchronized} 方法（锁对象 = 本实例），同一毫秒内的
 * 序列号分配因此串行。不使用 {@code AtomicLong} + CAS 自旋：序列耗尽与回拨等待两条等待路径要嵌进 CAS
 * 循环，自旋上界无法给出，正确性风险高于一把有界等待的锁。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class SnowflakeIdGenerator implements IdGenerator {

    /**
     * 纪元：2024-01-01T00:00:00Z 的毫秒值。冻结常量，不是配置项，不随版本变化。
     */
    public static final long EPOCH = 1704067200000L;

    /**
     * 时间戳段的左移位数，等于 workerId 位宽加序列位宽。
     */
    private static final long TIMESTAMP_SHIFT = 22L;

    /**
     * workerId 段的左移位数，等于序列位宽。
     */
    private static final long WORKER_ID_SHIFT = 12L;

    /**
     * 序列号最大值（12 位全 1），即单毫秒内可分配的最后一格。
     */
    private static final long MAX_SEQUENCE = 4095L;

    /**
     * 41 位时间戳字段的最大值；超出即无法装入位布局。
     */
    private static final long MAX_TIMESTAMP = (1L << 41) - 1L;

    /**
     * 允许等待追上的最大回拨毫秒数；超过即判定为系统性时钟故障。
     */
    private static final long MAX_BACKWARD_WAIT_MS = 5L;

    /**
     * 等待时钟追上的自旋间隔（毫秒）。
     */
    private static final long WAIT_INTERVAL_MS = 1L;

    /**
     * 本实例绑定的 workerId（10 位），构造期从来源取得。
     */
    private final long workerId;

    /**
     * workerId 来源，{@link #nextId()} 每次先查其健康状态。
     */
    private final WorkerIdSource source;

    /**
     * 时刻源，本模块唯一的时刻采样点。
     */
    private final LongSupplier clock;

    /**
     * 上一次成功分配使用的时间戳（毫秒）。
     */
    private long lastTimestamp = -1L;

    /**
     * 当前毫秒内已分配到的序列号。
     */
    private long sequence = 0L;

    /**
     * 以默认时刻源（系统毫秒时钟）构造生成器，供装配入口使用。
     *
     * @param source workerId 来源，构造期调用其 {@link WorkerIdSource#acquire()}
     * @throws IdException 来源给出的 workerId 不在 {@code 0..1023} 内
     */
    public SnowflakeIdGenerator(WorkerIdSource source) {
        this(source, System::currentTimeMillis);
    }

    /**
     * 以指定时刻源构造生成器，包级可见，仅供测试注入假时钟。
     *
     * @param source workerId 来源，构造期调用其 {@link WorkerIdSource#acquire()}
     * @param clock 时刻源，非空
     * @throws IdException 来源给出的 workerId 不在 {@code 0..1023} 内
     */
    SnowflakeIdGenerator(WorkerIdSource source, LongSupplier clock) {
        this.source = source;
        this.clock = clock;
        int acquired = source.acquire();
        if (acquired < IdNames.MIN_WORKER_ID || acquired > IdNames.MAX_WORKER_ID) {
            throw IdErrors.configOutOfRange(IdConfig.KEY_WORKER_ID, String.valueOf(acquired),
                    IdNames.MIN_WORKER_ID, IdNames.MAX_WORKER_ID);
        }
        this.workerId = acquired;
    }

    /**
     * 取下一个 ID：同一毫秒内序列号递增，序列耗尽等下一毫秒，短回拨等追上，长回拨失败。
     *
     * @return 下一个 ID，恒正且在本实例上严格递增
     * @throws IdException workerId 租约丢失、时钟早于 EPOCH、回拨超阈、时间戳溢出或等待被中断
     */
    @Override
    public synchronized long nextId() {
        if (!source.healthy()) {
            throw IdErrors.workerLeaseLost(workerId);
        }
        long now = clock.getAsLong();
        if (now < EPOCH) {
            throw IdErrors.clockBeforeEpoch(now);
        }
        if (now < lastTimestamp) {
            long backwards = lastTimestamp - now;
            if (backwards > MAX_BACKWARD_WAIT_MS) {
                throw IdErrors.clockBackwards(lastTimestamp, now, backwards, MAX_BACKWARD_WAIT_MS);
            }
            // 短回拨：等时钟追上后按「同一毫秒」继续，序列号继续 +1，不重置，因此不重号
            now = waitNextMillis(lastTimestamp);
        }
        if (now == lastTimestamp) {
            sequence = (sequence + 1L) & MAX_SEQUENCE;
            if (sequence == 0L) {
                // 本毫秒 4096 格用尽：等下一毫秒重新从 0 开始
                now = waitNextMillis(lastTimestamp + 1L);
                sequence = 0L;
            }
        } else {
            sequence = 0L;
        }
        if (now - EPOCH > MAX_TIMESTAMP) {
            throw IdErrors.timestampOverflow(now);
        }
        lastTimestamp = now;
        return ((now - EPOCH) << TIMESTAMP_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
    }

    /**
     * 等到时钟读数不小于目标值；回拨等待与「序列耗尽等下一毫秒」共用本方法。
     *
     * @param target 目标时间戳（毫秒）
     * @return 不小于 {@code target} 的时钟读数
     * @throws IdException 等待期间线程被中断（中断位已恢复）
     */
    private long waitNextMillis(long target) {
        long now = clock.getAsLong();
        while (now < target) {
            try {
                Thread.sleep(WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw IdErrors.waitInterrupted();
            }
            now = clock.getAsLong();
        }
        return now;
    }
}
