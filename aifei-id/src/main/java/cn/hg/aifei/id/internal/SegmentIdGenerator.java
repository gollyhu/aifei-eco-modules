/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

import cn.hg.aifei.id.IdException;
import cn.hg.aifei.id.IdGenerator;
import java.time.Duration;

/**
 * 号段实现的唯一定义点：号段分配、耗尽取段、回退检测都在本类。
 *
 * <p><b>单 buffer + 同步取段</b>：没有预取线程、没有异步状态机。分配路径是一把锁（{@code synchronized}，
 * 锁对象 = 本实例），段内取值是自增，号段用尽时在锁内同步取下一段。代价是「每 {@code step} 个 ID 一次
 * 存储往返」，换来的是无常驻线程、无「预取未就绪」等待、失败语义只剩一个点。</p>
 *
 * <p><b>回退检测</b>：新号段起点不大于历史最大段末端时抛 {@link IdException}。计数器过期、被清空或被
 * 手工重置都会触发它——这是本模块唯一能自动发现的数据安全网，把「静默重号」变成「显式失败」。</p>
 *
 * <p>段内严格递增但不连续：跨实例有空洞，进程崩溃会丢弃当前段剩余号。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class SegmentIdGenerator implements IdGenerator {

    /**
     * 号段存储。
     */
    private final SegmentStore store;

    /**
     * 号段键，决定唯一性域（不同 key 的序列互不相干）。
     */
    private final String key;

    /**
     * 号段长度。
     */
    private final int step;

    /**
     * 号段计数器有效期（毫秒），每次取段续期。
     */
    private final long ttlMillis;

    /**
     * 下一个待发值。
     */
    private long value = 1L;

    /**
     * 当前号段的末端（含）。
     */
    private long max = 0L;

    /**
     * 历史最大段末端，用于回退检测。
     */
    private long lastMax = 0L;

    /**
     * 构造号段生成器；首次 {@link #nextId()} 即触发取段。
     *
     * @param store 号段存储，非空
     * @param key 号段键，非空
     * @param step 号段长度，{@code 1..1000000}
     * @param ttlMillis 号段计数器有效期（毫秒）
     */
    public SegmentIdGenerator(SegmentStore store, String key, int step, long ttlMillis) {
        this.store = store;
        this.key = key;
        this.step = step;
        this.ttlMillis = ttlMillis;
    }

    /**
     * 取下一个 ID：段内有号则自增返回，号段用尽则在锁内同步取下一段。
     *
     * @return 下一个 ID，恒正且在本实例上严格递增
     * @throws IdException 取段失败（携带根因）或新号段回退
     */
    @Override
    public synchronized long nextId() {
        if (value > max) {
            refill();
        }
        return value++;
    }

    /**
     * 取下一段并推进游标：先校验回退，再统一更新当前段与历史段末端。
     *
     * @throws IdException 取段失败（携带根因）或新号段回退
     */
    private void refill() {
        long end;
        try {
            end = store.nextSegmentMax(key, step, Duration.ofMillis(ttlMillis));
        } catch (IdException e) {
            throw e;
        } catch (RuntimeException e) {
            throw IdErrors.segmentStoreFailed(key, e);
        }
        long start = end - step + 1L;
        if (start <= lastMax) {
            throw IdErrors.segmentRolledBack(lastMax, start, end);
        }
        max = end;
        lastMax = end;
        value = start;
    }
}
