/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.hg.aifei.id.IdException;
import cn.hg.aifei.id.support.FakeClock;
import cn.hg.aifei.id.support.FakeWorkerIdSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;

/**
 * 雪花算法单元测试：位布局、同毫秒序列、序列耗尽、回拨三分支、时钟与 workerId 的全部失败路径、并发唯一性。
 *
 * <p><b>为何位于 {@code internal} 包</b>：注入假时钟的双参构造是包级可见（只供测试使用），本包内才能构造。
 * 时钟全部由 {@link FakeClock} 编排，因此「等下一毫秒」与「等时钟追上」两条等待路径无需真实等待即可覆盖，
 * 序列耗尽用例也不需要 {@code Thread.sleep} 堆叠。</p>
 *
 * <p>覆盖时钟回拨、序列耗尽等场景；消息断言按固定文案逐字比对。</p>
 */
public class SnowflakeIdGeneratorTest {

    /**
     * 纪元毫秒值，取自实现常量（冻结数据格式）。
     */
    private static final long EPOCH = SnowflakeIdGenerator.EPOCH;

    /**
     * 用例基准时刻：纪元之后的第 12345 毫秒。
     */
    private static final long T0 = EPOCH + 12345L;

    /**
     * 时间戳段的右移位数（41 位时间戳 + 10 位 workerId + 12 位序列）。
     */
    private static final int TIMESTAMP_SHIFT = 22;

    /**
     * workerId 段的右移位数。
     */
    private static final int WORKER_ID_SHIFT = 12;

    /**
     * workerId 段与序列段的掩码。
     */
    private static final long WORKER_ID_MASK = 0x3FFL;

    /**
     * 序列段的掩码（12 位）。
     */
    private static final long SEQUENCE_MASK = 0xFFFL;

    // ─────────── 位布局与序列 ───────────

    /**
     * 位布局：1 符号位恒 0 + 41 位时间戳 + 10 位 workerId + 12 位序列，三段可按位反推。
     */
    @Test
    public void bitLayoutEncodesTimestampWorkerAndSequence() {
        SnowflakeIdGenerator generator = generator(new FakeClock(T0), 7);
        long id = generator.nextId();
        assertTrue("ID 必须恒正（符号位固定 0）", id > 0L);
        assertEquals(12345L, id >> TIMESTAMP_SHIFT);
        assertEquals(7L, (id >> WORKER_ID_SHIFT) & WORKER_ID_MASK);
        assertEquals(0L, id & SEQUENCE_MASK);
    }

    /**
     * 同一毫秒内序列号从 0 起逐个递增，返回值严格递增。
     */
    @Test
    public void sameMillisecondIncrementsSequence() {
        SnowflakeIdGenerator generator = generator(new FakeClock(T0).thenReturn(T0, 3), 1);
        long first = generator.nextId();
        long second = generator.nextId();
        long third = generator.nextId();
        assertEquals(0L, first & SEQUENCE_MASK);
        assertEquals(1L, second & SEQUENCE_MASK);
        assertEquals(2L, third & SEQUENCE_MASK);
        assertTrue(first < second && second < third);
        assertEquals(T0 - EPOCH, third >> TIMESTAMP_SHIFT);
    }

    /**
     * 场景 7：同一毫秒 4096 格用尽后等下一毫秒继续，第 4097 个号落到下一毫秒且序列号归零。
     */
    @Test
    public void sequenceExhaustionWaitsForNextMillisecond() {
        int capacity = 4096;
        SnowflakeIdGenerator generator =
                generator(new FakeClock(T0).thenReturn(T0, capacity + 1).thenReturn(T0 + 1), 2);
        long[] ids = new long[capacity + 1];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = generator.nextId();
        }
        assertEquals(0L, ids[0] & SEQUENCE_MASK);
        assertEquals(4095L, ids[capacity - 1] & SEQUENCE_MASK);
        assertEquals(T0 - EPOCH, ids[capacity - 1] >> TIMESTAMP_SHIFT);
        long overflowed = ids[capacity];
        assertEquals(T0 + 1 - EPOCH, overflowed >> TIMESTAMP_SHIFT);
        assertEquals(0L, overflowed & SEQUENCE_MASK);
        assertEquals(2L, (overflowed >> WORKER_ID_SHIFT) & WORKER_ID_MASK);
        assertTrue("越界的下一个号必须仍大于同毫秒内的最后一个号", overflowed > ids[capacity - 1]);
    }

    // ─────────── 时钟回拨 ───────────

    /**
     * 场景 2：回拨超过 5ms 容差直接失败（消息 B 逐字）。
     */
    @Test
    public void clockBackwardsBeyondToleranceRejected() {
        SnowflakeIdGenerator generator = generator(new FakeClock(T0).thenReturn(T0).thenReturn(T0 - 10L), 3);
        generator.nextId();
        try {
            generator.nextId();
            fail("回拨超过 5ms 应抛出 IdException");
        } catch (IdException e) {
            assertEquals("clock moved backwards beyond tolerance, lastTimestamp: " + T0
                    + ", now: " + (T0 - 10L) + ", backwards: 10ms, tolerance: 5ms", e.getMessage());
        }
    }

    /**
     * 场景 8：回拨不超过 5ms 时等时钟追上后按「同一毫秒」继续，序列号 +1（不重置，故不重号）。
     */
    @Test
    public void shortBackwardsWaitsThenContinues() {
        SnowflakeIdGenerator generator =
                generator(new FakeClock(T0).thenReturn(T0).thenReturn(T0 - 3L).thenReturn(T0), 4);
        long first = generator.nextId();
        long second = generator.nextId();
        assertTrue("追上后必须继续递增", second > first);
        assertEquals(T0 - EPOCH, second >> TIMESTAMP_SHIFT);
        assertEquals(1L, second & SEQUENCE_MASK);
    }

    /**
     * 场景 6：回拨等待期间线程被中断，恢复中断位并拒绝发号（消息 F 逐字）。
     */
    @Test
    public void interruptedWhileWaitingRejected() {
        SnowflakeIdGenerator generator = generator(new FakeClock(T0).thenReturn(T0).thenReturn(T0 - 4L), 5);
        generator.nextId();
        Thread.currentThread().interrupt();
        try {
            generator.nextId();
            fail("等待被中断应抛出 IdException");
        } catch (IdException e) {
            assertEquals("interrupted while waiting for clock to catch up", e.getMessage());
        } finally {
            assertTrue("中断位必须被恢复", Thread.interrupted());
        }
    }

    // ─────────── 时钟边界 ───────────

    /**
     * 场景 3：系统时钟早于纪元时拒绝发号（消息 C 逐字）。
     */
    @Test
    public void clockBeforeEpochRejected() {
        SnowflakeIdGenerator generator = generator(new FakeClock(EPOCH - 1L), 6);
        try {
            generator.nextId();
            fail("时钟早于纪元应抛出 IdException");
        } catch (IdException e) {
            assertEquals("clock is before aifei-id epoch 2024-01-01T00:00:00Z, now: "
                    + (EPOCH - 1L), e.getMessage());
        }
    }

    /**
     * 场景 4：41 位时间戳字段耗尽时拒绝发号（消息 D 逐字）。
     */
    @Test
    public void timestampOverflowRejected() {
        long overflowNow = EPOCH + (1L << 41);
        SnowflakeIdGenerator generator = generator(new FakeClock(T0).thenReturn(T0).thenReturn(overflowNow), 7);
        generator.nextId();
        try {
            generator.nextId();
            fail("时间戳溢出应抛出 IdException");
        } catch (IdException e) {
            assertEquals("timestamp overflow, the 41-bit timestamp field of snowflake is exhausted, "
                    + "now: " + overflowNow, e.getMessage());
        }
    }

    // ─────────── workerId 来源 ───────────

    /**
     * 场景 5：租约丢失后拒绝发号（消息 E 逐字，含 workerId）。
     */
    @Test
    public void workerLeaseLostRejected() {
        FakeWorkerIdSource source = new FakeWorkerIdSource(9);
        SnowflakeIdGenerator generator =
                new SnowflakeIdGenerator(source, new FakeClock(T0));
        assertTrue(generator.nextId() > 0L);
        source.breakLease();
        try {
            generator.nextId();
            fail("租约丢失应抛出 IdException");
        } catch (IdException e) {
            assertEquals("worker id lease lost, workerId: 9, stop generating ids to avoid duplicates",
                    e.getMessage());
        }
    }

    /**
     * workerId 越界在构造期即被拒绝（10 位字段装不下），消息复用配置越界措辞。
     */
    @Test
    public void workerIdOutOfRangeRejected() {
        try {
            new SnowflakeIdGenerator(new FakeWorkerIdSource(1024), new FakeClock(T0));
            fail("workerId 越界应在构造期抛出 IdException");
        } catch (IdException e) {
            assertEquals("id.workerId must be in [0, 1023], but was: 1024", e.getMessage());
        }
    }

    /**
     * 诊断路径重复调用 {@code acquire()} 不重复消耗编号，取到的仍是同一 workerId。
     */
    @Test
    public void acquireIsIdempotent() {
        FakeWorkerIdSource source = new FakeWorkerIdSource(1023);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(source, new FakeClock(T0));
        long id = generator.nextId();
        assertEquals(1023L, (id >> WORKER_ID_SHIFT) & WORKER_ID_MASK);
        assertEquals(1, source.acquireCount());
    }

    // ─────────── 并发 ───────────

    /**
     * 多线程并发生成不重号，且每个线程内部的 ID 严格递增。
     */
    @Test
    public void concurrentGenerationIsUniqueAndIncreasing() throws InterruptedException {
        final int threadCount = 8;
        final int idsPerThread = 1000;
        final SnowflakeIdGenerator generator = new SnowflakeIdGenerator(new FakeWorkerIdSource(3));
        final CountDownLatch startGate = new CountDownLatch(1);
        final List<List<Long>> buckets = new ArrayList<>();
        List<Thread> workers = new ArrayList<>();
        final List<Throwable> failures = new ArrayList<>();
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

    /**
     * 默认构造（真实系统时钟）可用，且连续两个号严格递增。
     */
    @Test
    public void defaultConstructorUsesSystemClock() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(new FakeWorkerIdSource(0));
        long first = generator.nextId();
        long second = generator.nextId();
        assertTrue(first > 0L);
        assertTrue(second > first);
        assertEquals(0L, (second >> WORKER_ID_SHIFT) & WORKER_ID_MASK);
    }

    // ─────────── 工具 ───────────

    /**
     * 以假时钟与 workerId 构造生成器。
     *
     * @param clock 假时钟
     * @param workerId workerId
     * @return 生成器
     */
    private static SnowflakeIdGenerator generator(FakeClock clock, int workerId) {
        return new SnowflakeIdGenerator(new FakeWorkerIdSource(workerId), clock);
    }
}
