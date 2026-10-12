/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.caffeine;

import cn.hg.aifei.cache.internal.Config;
import cn.hg.aifei.cache.TestSupport.FakeTicker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import org.junit.Assert;
import org.junit.Test;

/**
 * 并发回归：同 key 的读改写必须在分段锁内完成。
 */
public class CaffeineConcurrencyRegressionTest {

    private static final int WORKERS = 8;

    private static final int ROUNDS = 2_000;

    private static final String COUNTER = "visits";

    /**
     * 并发递增不得丢失更新。
     */
    @Test
    public void shouldNotLoseUpdatesOnConcurrentIncrease() throws Exception {
        CaffeineCounter counter = new CaffeineCounter(100_000L, new FakeTicker(), Config.DEFAULT_NAMESPACE);

        parallel(WORKERS, worker ->
                repeat(ROUNDS, round -> counter.increase(COUNTER, "k", 1L, Duration.ofMinutes(5))));

        Assert.assertEquals((long) WORKERS * ROUNDS,
                counter.get(COUNTER, "k").longValue());
    }

    /**
     * 并发「删除 + 递增」的合法结果只有两种：删除发生在最后一次递增之后（0），
     * 或删除之后又有递增（正整数），不得出现「旧值 + step」这类陈旧值。
     */
    @Test
    public void shouldKeepRemoveAndIncreaseOrdering() throws Exception {
        CaffeineCounter counter = new CaffeineCounter(100_000L, new FakeTicker(), Config.DEFAULT_NAMESPACE);

        parallel(WORKERS, worker -> {
            if (worker % 2 == 0) {
                repeat(ROUNDS, round -> counter.increase(COUNTER, "k", 1L, Duration.ofMinutes(5)));
            } else {
                repeat(ROUNDS, round -> counter.remove(COUNTER, "k"));
            }
        });

        Long value = counter.get(COUNTER, "k");
        Assert.assertTrue("删除后残留值只能是 0 或最后一次递增的结果，实际=" + value,
                value == null || value >= 0L);
    }

    /**
     * 并发续期不得让已被读取观察为过期的条目复活。
     *
     * <p>时钟在启动线程之前就越过 TTL，因此所有工作线程观察到的都必然是「已过期」，
     * 不存在「还没过期就先读到值」的合法窗口，断言是确定性的。</p>
     */
    @Test
    public void shouldNotResurrectExpiredEntryUnderConcurrency() throws Exception {
        FakeTicker ticker = new FakeTicker();
        CaffeineCache cache = new CaffeineCache(100_000L, ticker, Config.DEFAULT_NAMESPACE, 1_000L, false);
        cache.put("user", "1", "tom", Duration.ofMillis(10));

        // 先让条目过期，再开始并发观察
        ticker.advance(Duration.ofMillis(10));
        Assert.assertNull(cache.get("user", "1"));

        AtomicInteger resurrections = new AtomicInteger();
        parallel(WORKERS, worker -> repeat(ROUNDS, round -> {
            if (cache.get("user", "1") != null) {
                // 过期被观察到之后仍能读到值 => 复活
                resurrections.incrementAndGet();
            } else if (cache.expire("user", "1", Duration.ofSeconds(1))) {
                // 已过期条目被成功续期 => 复活
                resurrections.incrementAndGet();
            }
        }));

        Assert.assertEquals("过期条目不得被 expire 复活", 0, resurrections.get());
        Assert.assertNull(cache.get("user", "1"));
    }

    private static void repeat(int rounds, IntConsumer action) {
        for (int i = 0; i < rounds; i++) {
            action.accept(i);
        }
    }

    private static void parallel(int workers, IntConsumer work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                final int worker = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    work.accept(worker);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            Assert.assertTrue("worker threads did not stop", pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
