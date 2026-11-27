/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.hg.aifei.id.internal.LocalSegmentStore;
import cn.hg.aifei.id.internal.SegmentIdGenerator;
import cn.hg.aifei.id.internal.SegmentStore;
import cn.hg.aifei.id.support.FakeSegmentStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;

/**
 * 号段生成器单元测试：段内连续、耗尽取段、取段参数下传、取段失败包装、回退拦截、本地退化与并发唯一性。
 *
 * <p>两个存储实现各司其职：{@link FakeSegmentStore} 负责「按脚本给段末端 / 注入故障」的精确用例（含回退），
 * {@link LocalSegmentStore} 负责真实推进与并发用例——本地实现无网络、无过期，适合做并发唯一性验证。</p>
 *
 * <p>覆盖段推进与回退场景；消息断言按固定文案逐字比对。</p>
 */
public class SegmentIdGeneratorTest {

    /**
     * 默认号段键。
     */
    private static final String DEFAULT_KEY = "default";

    /**
     * 用例使用的状态有效期（毫秒）。
     */
    private static final long TTL_MILLIS = 123456L;

    // ─────────── 段内连续与取段 ───────────

    /**
     * 首次发号即取段，段内从 {@code end - step + 1} 起连续递增。
     */
    @Test
    public void firstSegmentStartsFromOneAndStaysContiguous() {
        FakeSegmentStore store = new FakeSegmentStore().thenReturn(3L);
        SegmentIdGenerator generator = new SegmentIdGenerator(store, DEFAULT_KEY, 3, TTL_MILLIS);
        assertEquals(1L, generator.nextId());
        assertEquals(2L, generator.nextId());
        assertEquals(3L, generator.nextId());
        assertEquals("段内不应重复取段", 1, store.calls());
    }

    /**
     * 号段用尽时在锁内同步取下一段，跨段继续递增且不重号。
     */
    @Test
    public void refillsAfterSegmentExhausted() {
        FakeSegmentStore store = new FakeSegmentStore().thenReturn(2L).thenReturn(4L);
        SegmentIdGenerator generator = new SegmentIdGenerator(store, "orders", 2, TTL_MILLIS);
        assertEquals(1L, generator.nextId());
        assertEquals(2L, generator.nextId());
        assertEquals(3L, generator.nextId());
        assertEquals(4L, generator.nextId());
        assertEquals(2, store.calls());
    }

    /**
     * 取段时把键名、段长与有效期如实下传给存储。
     */
    @Test
    public void storeReceivesKeyStepAndTtl() {
        FakeSegmentStore store = new FakeSegmentStore().thenReturn(5L);
        SegmentIdGenerator generator = new SegmentIdGenerator(store, "orders", 5, TTL_MILLIS);
        assertEquals(1L, generator.nextId());
        assertEquals("orders", store.lastKey());
        assertEquals(5, store.lastStep());
        assertEquals(Duration.ofMillis(TTL_MILLIS), store.lastTtl());
    }

    // ─────────── 失败路径 ───────────

    /**
     * 场景 9：取段时存储抛异常被包装为模块异常，消息 G 逐字且保留根因。
     */
    @Test
    public void storeFailureWrappedWithCause() {
        IllegalStateException cause = new IllegalStateException("redis timeout");
        FakeSegmentStore store = new FakeSegmentStore().failWith(cause);
        SegmentIdGenerator generator = new SegmentIdGenerator(store, "orders", 10, TTL_MILLIS);
        try {
            generator.nextId();
            fail("取段失败应抛出 IdException");
        } catch (IdException e) {
            assertEquals("segment store failed, cache: __ID__, key: orders, cause: redis timeout",
                    e.getMessage());
            assertSame("必须保留根因，便于定位底层存储问题", cause, e.getCause());
        }
    }

    /**
     * 场景 11：新段起点不大于上次段末端（计数器过期或被重置）时拒绝发号，消息 I 逐字。
     */
    @Test
    public void rollbackRejected() {
        FakeSegmentStore store = new FakeSegmentStore().thenReturn(2L).thenReturn(2L);
        SegmentIdGenerator generator = new SegmentIdGenerator(store, "orders", 2, TTL_MILLIS);
        assertEquals(1L, generator.nextId());
        assertEquals(2L, generator.nextId());
        try {
            generator.nextId();
            fail("号段回退应抛出 IdException");
        } catch (IdException e) {
            assertEquals("segment rolled back, lastMax: 2, newSegment: [1, 2], "
                    + "the counter may have expired or been reset", e.getMessage());
        }
    }

    // ─────────── 本地退化 ───────────

    /**
     * {@code local} 存储按 key 各自一条序列，互不干扰。
     */
    @Test
    public void localStoreKeepsKeysIndependent() {
        SegmentStore store = new LocalSegmentStore();
        SegmentIdGenerator orders = new SegmentIdGenerator(store, "orders", 3, TTL_MILLIS);
        SegmentIdGenerator invoices = new SegmentIdGenerator(store, "invoices", 3, TTL_MILLIS);
        assertEquals(1L, orders.nextId());
        assertEquals(1L, invoices.nextId());
        assertEquals(2L, orders.nextId());
        assertEquals(3L, orders.nextId());
        assertEquals("orders 号段用尽后继续下一段", 4L, orders.nextId());
        assertEquals(2L, invoices.nextId());
    }

    /**
     * 本地退化「重启重号」：新进程（新存储实例）从 1 重新开始，与重启前的号重叠。
     */
    @Test
    public void localStoreRestartsFromOne() {
        SegmentIdGenerator beforeRestart =
                new SegmentIdGenerator(new LocalSegmentStore(), "orders", 3, TTL_MILLIS);
        assertEquals(1L, beforeRestart.nextId());
        assertEquals(2L, beforeRestart.nextId());
        SegmentIdGenerator afterRestart =
                new SegmentIdGenerator(new LocalSegmentStore(), "orders", 3, TTL_MILLIS);
        assertEquals("本地退化重启后必须重号（仅限单实例与开发测试）", 1L, afterRestart.nextId());
    }

    /**
     * 并发取号不重号，且每个线程内部严格递增（号段分配在锁内串行）。
     */
    @Test
    public void concurrentGenerationIsUniqueAndIncreasing() throws InterruptedException {
        final int threadCount = 8;
        final int idsPerThread = 500;
        final SegmentIdGenerator generator =
                new SegmentIdGenerator(new LocalSegmentStore(), "orders", 64, TTL_MILLIS);
        final CountDownLatch startGate = new CountDownLatch(1);
        final List<List<Long>> buckets = new ArrayList<>();
        final List<Throwable> failures = new ArrayList<>();
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final List<Long> bucket = new ArrayList<>(idsPerThread);
            buckets.add(bucket);
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startGate.await();
                        for (int n = 0; n < idsPerThread; n++) {
                            bucket.add(Long.valueOf(generator.nextId()));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        synchronized (failures) {
                            failures.add(e);
                        }
                    } catch (RuntimeException e) {
                        synchronized (failures) {
                            failures.add(e);
                        }
                    }
                }
            });
            workers.add(worker);
            worker.start();
        }
        startGate.countDown();
        for (Thread worker : workers) {
            worker.join(30000L);
            assertFalse("生成线程应在超时前结束", worker.isAlive());
        }
        assertTrue("生成线程不应抛出异常：" + failures, failures.isEmpty());

        Set<Long> unique = new HashSet<>();
        for (List<Long> bucket : buckets) {
            assertEquals(idsPerThread, bucket.size());
            long previous = 0L;
            for (Long id : bucket) {
                assertTrue("ID 必须恒正", id.longValue() > 0L);
                if (previous != 0L) {
                    assertTrue("同一线程内必须严格递增", id.longValue() > previous);
                }
                previous = id.longValue();
            }
            unique.addAll(bucket);
        }
        assertEquals(threadCount * idsPerThread, unique.size());
    }
}
