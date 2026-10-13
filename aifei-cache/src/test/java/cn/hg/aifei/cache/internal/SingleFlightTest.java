/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.CacheKeyMatchType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 同 JVM 回源单飞：并发未命中同一缓存项时，{@code loader} 至多被调用一次。
 *
 * <p>用例的内部约定：所有并发任务在同一个栅栏上起跑，随后各自对 {@code allArrived} 计数，装载器
 * 等待该计数归零后再留一小段余量再返回。于是「全部线程都已抵达装载入口」成为可判定事实，余量只
 * 用来吸收调度抖动，不承担正确性论证。</p>
 *
 * <p>本组用例与「登记表」是承重关系：摘掉在途登记，{@link #shouldCallLoaderOnceUnderConcurrentMiss}
 * 与 {@link #shouldReuseResultWhenNotWritable} 必须恰好转红，其余用例仍绿。</p>
 */
public class SingleFlightTest {

    private static final Duration TTL = Duration.ofSeconds(60);

    /**
     * 并发未命中时装载器只跑一次，且所有线程拿到同一个值。
     */
    @Test
    public void shouldCallLoaderOnceUnderConcurrentMiss() throws Exception {
        CountingCache cache = new CountingCache("ns-once", false);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch allArrived = new CountDownLatch(8);

        Supplier<String> loader = () -> {
            loads.incrementAndGet();
            awaitQuietly(allArrived);
            sleepQuietly();
            return "V";
        };

        Outcomes outcomes = runConcurrently(8, allArrived, () -> cache.get("user", "1", TTL, loader));

        assertNoErrors(outcomes);
        assertEquals(1, loads.get());
        assertEquals(1, cache.putCalls());
        for (String value : outcomes.values) {
            assertEquals("V", value);
        }
    }

    /**
     * {@code cacheNull = false} 且装载器返回 {@code null} 时结果不可写入，跟随者仍必须复用胜者的结果，
     * 否则每个线程都会各自回源一次（以「结果可写入」为前提的实现覆盖不到的空洞）。
     */
    @Test
    public void shouldReuseResultWhenNotWritable() throws Exception {
        CountingCache cache = new CountingCache("ns-not-writable", false);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch allArrived = new CountDownLatch(8);

        Supplier<String> loader = () -> {
            loads.incrementAndGet();
            awaitQuietly(allArrived);
            sleepQuietly();
            return null;
        };

        Outcomes outcomes = runConcurrently(8, allArrived, () -> cache.get("user", "2", TTL, loader));

        assertNoErrors(outcomes);
        assertEquals(1, loads.get());
        assertEquals(1, cache.putCalls());
        assertFalse(cache.exists("user", "2"));
        for (String value : outcomes.values) {
            assertNull(value);
        }
    }

    /**
     * {@code cacheNull = true} 时否定结果落成哨兵，跟随者复用胜者发布的 {@code null}，同样只回源一次。
     */
    @Test
    public void shouldReuseNegativeResult() throws Exception {
        CountingCache cache = new CountingCache("ns-negative", true);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch allArrived = new CountDownLatch(8);

        Supplier<String> loader = () -> {
            loads.incrementAndGet();
            awaitQuietly(allArrived);
            sleepQuietly();
            return null;
        };

        Outcomes outcomes = runConcurrently(8, allArrived, () -> cache.get("user", "3", TTL, loader));

        assertNoErrors(outcomes);
        assertEquals(1, loads.get());
        assertTrue(cache.exists("user", "3"));
        for (String value : outcomes.values) {
            assertNull(value);
        }
    }

    /**
     * 预检与登记之间的窗口：预检仍报未命中，但进入单飞时先到者已把值写入存储，双检必须命中该值。
     */
    @Test
    public void shouldReuseValueWrittenByWinnerOnLateMiss() throws Exception {
        PrecheckMissCache cache = new PrecheckMissCache("ns-late");
        AtomicInteger loads = new AtomicInteger();

        // 预检：此刻仍未命中（同一读取顺带模拟先到者随后完成写入）
        assertNull(cache.get("user", "5"));

        String result = SingleFlight.load(cache, "user", "5", TTL.toMillis(), () -> {
            loads.incrementAndGet();
            return "loaded";
        });

        assertEquals("V", result);
        assertEquals(0, loads.get());
    }

    /**
     * 胜者装载失败时，跟随者看到同型同消息的异常，且下游仍只被打了一次。
     */
    @Test
    public void shouldPropagateWinnerFailureToFollowers() throws Exception {
        CountingCache cache = new CountingCache("ns-failure", false);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch allArrived = new CountDownLatch(4);

        Supplier<String> loader = () -> {
            loads.incrementAndGet();
            awaitQuietly(allArrived);
            sleepQuietly();
            throw new IllegalStateException("boom");
        };

        Outcomes outcomes = runConcurrently(4, allArrived, () -> cache.get("user", "6", TTL, loader));

        assertEquals(1, loads.get());
        for (Throwable failure : outcomes.errors) {
            assertTrue("期望 IllegalStateException，实际 " + failure, failure instanceof IllegalStateException);
            assertEquals("boom", failure.getMessage());
        }
    }

    /**
     * 等待被中断的线程放弃复用，退化为自行回源，不影响其它线程。
     */
    @Test
    public void shouldDegradeWhenWaitInterrupted() throws Exception {
        CountingCache cache = new CountingCache("ns-interrupted", false);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch winnerInLoader = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Supplier<String> loader = () -> {
            if (loads.incrementAndGet() == 1) {
                winnerInLoader.countDown();
                awaitQuietly(release);
                return "winner";
            }
            return "degraded";
        };

        String[] results = new String[2];
        Thread winner = new Thread(() -> results[0] = cache.get("user", "7", TTL, loader));
        winner.start();
        assertTrue(winnerInLoader.await(5L, TimeUnit.SECONDS));

        Thread follower = new Thread(() -> results[1] = cache.get("user", "7", TTL, loader));
        follower.start();
        sleepQuietly();
        follower.interrupt();
        follower.join(5_000L);

        assertEquals("degraded", results[1]);
        assertEquals(2, loads.get());

        release.countDown();
        winner.join(5_000L);
        assertEquals("winner", results[0]);
    }

    // ─────────────────────────────── 支撑 ───────────────────────────────

    /**
     * 一次并发跑的汇总：成功线程留在 {@code values}，异常线程留在 {@code errors}。
     */
    private static final class Outcomes {

        final String[] values;

        final Throwable[] errors;

        Outcomes(int threads) {
            this.values = new String[threads];
            this.errors = new Throwable[threads];
        }
    }

    /**
     * 让 {@code threads} 个线程在栅栏上同时起跑，各自对 {@code allArrived} 计数后执行 {@code action}。
     */
    private static Outcomes runConcurrently(int threads, CountDownLatch allArrived, Supplier<String> action)
            throws InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(threads);
        Outcomes outcomes = new Outcomes(threads);
        List<Thread> workers = new ArrayList<Thread>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            workers.add(new Thread(() -> {
                try {
                    barrier.await();
                } catch (Throwable e) {
                    outcomes.errors[index] = e;
                    allArrived.countDown();
                    return;
                }
                allArrived.countDown();
                try {
                    outcomes.values[index] = action.get();
                } catch (Throwable e) {
                    outcomes.errors[index] = e;
                }
            }));
        }
        for (Thread worker : workers) {
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join(20_000L);
        }
        for (Thread worker : workers) {
            if (worker.isAlive()) {
                throw new AssertionError("并发任务未在限期内结束");
            }
        }
        return outcomes;
    }

    private static void assertNoErrors(Outcomes outcomes) {
        for (Throwable error : outcomes.errors) {
            if (error != null) {
                throw new AssertionError("线程失败: " + error, error);
            }
        }
    }

    /**
     * 等待归零，限时以避免装载器在异常路径上永久阻塞。
     */
    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 吸收调度抖动的固定余量；不承担正确性论证。
     */
    private static void sleepQuietly() {
        try {
            Thread.sleep(200L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 内存存储桩：只用 {@code namespace + cacheName + key} 定位，并按 {@code cacheNull} 决定
     * {@code null} 是否落盘；{@code put} 调用次数可观测，作为「谁真正回写了」的证据。
     */
    static class CountingCache implements Cache {

        private static final Object NULL_MARKER = new Object();

        private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<String, Object>();

        private final AtomicInteger putCalls = new AtomicInteger();

        private final String namespace;

        private final boolean cacheNull;

        CountingCache(String namespace, boolean cacheNull) {
            this.namespace = namespace;
            this.cacheNull = cacheNull;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T get(String cacheName, String key) {
            Object stored = store.get(slot(cacheName, key));
            if (stored == null || stored == NULL_MARKER) {
                return null;
            }
            return (T) stored;
        }

        @Override
        public boolean exists(String cacheName, String key) {
            return store.containsKey(slot(cacheName, key));
        }

        @Override
        public List<String> keys(String cacheName, int offset, int limit, String pattern,
                                 CacheKeyMatchType matchType) {
            return Collections.emptyList();
        }

        @Override
        public void put(String cacheName, String key, Object value, Duration ttl) {
            putCalls.incrementAndGet();
            if (value == null) {
                if (!cacheNull) {
                    return;
                }
                store.put(slot(cacheName, key), NULL_MARKER);
                return;
            }
            store.put(slot(cacheName, key), value);
        }

        @Override
        public boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl) {
            return false;
        }

        @Override
        public boolean expire(String cacheName, String key, Duration ttl) {
            return false;
        }

        @Override
        public void remove(String cacheName, String key) {
            store.remove(slot(cacheName, key));
        }

        @Override
        public void clear(String cacheName) {
            store.clear();
        }

        @Override
        public Duration getTtl(String cacheName, String key) {
            return null;
        }

        @Override
        public Set<String> cacheNames() {
            return Collections.emptySet();
        }

        @Override
        public Duration ttl() {
            return TTL;
        }

        @Override
        public boolean cacheNull() {
            return cacheNull;
        }

        @Override
        public String namespace() {
            return namespace;
        }

        int putCalls() {
            return putCalls.get();
        }

        private static String slot(String cacheName, String key) {
            return cacheName + "|" + key;
        }
    }

    /**
     * 首次读取表现为未命中、随后可见「先到者写入的值」的存储桩，用来复现双检所护住的那段窗口。
     */
    static final class PrecheckMissCache extends CountingCache {

        private final AtomicInteger reads = new AtomicInteger();

        PrecheckMissCache(String namespace) {
            super(namespace, false);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T get(String cacheName, String key) {
            if (reads.incrementAndGet() == 1) {
                put(cacheName, key, "V", TTL);
                return null;
            }
            return super.get(cacheName, key);
        }
    }
}
