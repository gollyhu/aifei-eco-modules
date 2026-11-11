/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import cn.hg.aifei.id.internal.CacheSegmentStore;
import cn.hg.aifei.id.internal.IdNames;
import cn.hg.aifei.id.support.FakeCounter;
import java.time.Duration;
import org.junit.Test;

/**
 * 共享存储号段实现单元测试：分区名与段键拼装、段长与有效期下传、返回值语义与异常职责划分。
 *
 * <p>用 {@link FakeCounter} 顶替 {@code aifei-cache} 的真实计数器：本实现只负责「把逻辑键与参数如实交给
 * 计数器，并把计数值当段末端返回」，不引入任何后端、不联网，因此用例完全可重复。</p>
 */
public class CacheSegmentStoreTest {

    /**
     * 用例使用的号段键。
     */
    private static final String KEY = "orders";

    /**
     * 用例使用的段长。
     */
    private static final int STEP = 500;

    // ─────────── 参数下传 ───────────

    /**
     * 计数器分区固定为保留分区，键为 {@code segment.<id.segment.key>}，段长与有效期如实下传。
     */
    @Test
    public void usesReservedPartitionAndSegmentKey() {
        FakeCounter counter = new FakeCounter();
        CacheSegmentStore store = new CacheSegmentStore(counter);
        assertEquals(500L, store.nextSegmentMax(KEY, STEP, Duration.ofMillis(1234L)));
        assertEquals("计数器必须落在保留分区", "__ID__", counter.lastCounterName());
        assertEquals("段键格式属冻结数据格式", "segment.orders", counter.lastKey());
        assertEquals(STEP, counter.lastStep());
        assertEquals(Duration.ofMillis(1234L), counter.lastTtl());
    }

    // ─────────── 返回值语义 ───────────

    /**
     * 返回值就是计数器自增后的计数值，即新号段的末端（号段 = [返回值 - step + 1, 返回值]）。
     */
    @Test
    public void returnsCounterValueAsSegmentEnd() {
        FakeCounter counter = new FakeCounter();
        CacheSegmentStore store = new CacheSegmentStore(counter);
        Duration ttl = Duration.ofMillis(60000L);
        assertEquals(500L, store.nextSegmentMax(KEY, STEP, ttl));
        assertEquals(1000L, store.nextSegmentMax(KEY, STEP, ttl));
        assertEquals(1500L, store.nextSegmentMax(KEY, STEP, ttl));
        assertEquals(1500L, counter.valueOf(IdNames.CACHE_NAME, IdNames.segmentKey(KEY)));
    }

    // ─────────── 异常职责 ───────────

    /**
     * 不同号段键各自一条计数序列，彼此不推进对方。
     */
    @Test
    public void differentKeysUseDifferentCounters() {
        FakeCounter counter = new FakeCounter();
        CacheSegmentStore store = new CacheSegmentStore(counter);
        Duration ttl = Duration.ofMillis(60000L);
        assertEquals(10L, store.nextSegmentMax("orders", 10, ttl));
        assertEquals(10L, store.nextSegmentMax("invoices", 10, ttl));
        assertEquals(20L, store.nextSegmentMax("orders", 10, ttl));
    }

    /**
     * 计数器抛出的运行时异常原样向上抛出：包装与消息统一由号段生成器完成，本实现不重复包装。
     */
    @Test
    public void counterFailurePropagatesUnwrapped() {
        IllegalStateException failure = new IllegalStateException("redis down");
        FakeCounter counter = new FakeCounter().failWith(failure);
        CacheSegmentStore store = new CacheSegmentStore(counter);
        try {
            store.nextSegmentMax(KEY, STEP, Duration.ofMillis(60000L));
            fail("计数器故障应向上抛出");
        } catch (IllegalStateException e) {
            assertSame("本实现不包装异常，根因必须原样上抛", failure, e);
        }
    }
}
