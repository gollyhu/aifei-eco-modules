/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

import cn.hg.aifei.cache.Counter;
import java.time.Duration;

/**
 * 共享存储实现：号段末端由 {@link Counter#increaseAndRefreshTtl(String, String, long, Duration)} 的一次
 * 原子自增给出（Redis 侧即 {@code INCRBY}），并顺带把计数器有效期重置为传入值。
 *
 * <p>计数器落在主缓存的 {@link IdNames#CACHE_NAME} 保留分区，键为
 * {@code segment.<id.segment.key>}；物理 key 前缀由缓存实例的 {@code namespace} 负责，本类只给逻辑键。</p>
 *
 * <p>本类不做异常包装与回退检测：包装由 {@link SegmentIdGenerator} 统一完成（保证「取段失败」只有
 * 一种消息与一个包装点），回退检测同属生成器的唯一职责。无状态、无线程，线程安全由 {@link Counter}
 * 保证。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class CacheSegmentStore implements SegmentStore {

    /**
     * 主缓存派生出的计数器，要求后端为共享存储。
     */
    private final Counter counter;

    /**
     * 以共享计数器构造存储。
     *
     * @param counter 主计数器，由 {@link CacheAccess#requireSharedCounter()} 提供
     */
    public CacheSegmentStore(Counter counter) {
        this.counter = counter;
    }

    /**
     * 原子自增 {@code step} 并续期，返回新号段末端。
     *
     * @param key 号段键，非空
     * @param step 号段长度，{@code 1..1000000}
     * @param ttl 号段计数器的有效期，本次取段即续期
     * @return 新号段末端（含），号段即 {@code [返回值 - step + 1, 返回值]}
     */
    @Override
    public long nextSegmentMax(String key, int step, Duration ttl) {
        return counter.increaseAndRefreshTtl(IdNames.CACHE_NAME, IdNames.segmentKey(key), step, ttl);
    }
}
