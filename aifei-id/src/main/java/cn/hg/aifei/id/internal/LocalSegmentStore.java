/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地退化实现：用进程内计数代替共享存储，按 key 各自推进，无过期、无锁、无网络。
 *
 * <p><b>重启即重号</b>：进程重启后计数从 0 开始，首个号段仍是 {@code [1, step]}，与重启前发出的 ID
 * 重叠。因此本实现只适用于<b>单实例</b>与开发 / 测试；多实例或需要跨重启唯一时必须用
 * {@code id.segment.store=cache}（显式开启 {@code local} 才不会误入此模式）。</p>
 *
 * <p>线程安全：计数放在 {@link ConcurrentHashMap} 里，用一次原子的 {@code merge} 完成「读 + 加 + 写」，
 * 多个线程并发取段不会拿到重叠区间。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class LocalSegmentStore implements SegmentStore {

    /**
     * 每个 key 的累计计数值，等价于共享后端里计数器的当前值。
     */
    private final ConcurrentHashMap<String, Long> counters = new ConcurrentHashMap<>();

    /**
     * 领走一个新号段：把该 key 的累计计数原子加上 {@code step}，返回加完后的值。
     *
     * @param key 号段键，非空
     * @param step 号段长度，{@code 1..1000000}
     * @param ttl 号段计数器的有效期；本地实现无过期语义，忽略该参数
     * @return 新号段末端（含），号段即 {@code [返回值 - step + 1, 返回值]}
     */
    @Override
    public long nextSegmentMax(String key, int step, Duration ttl) {
        return counters.merge(key, (long) step, (previous, increment) -> previous + increment);
    }
}
