/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.aifei.log.LogKit;
import cn.hg.aifei.lock.internal.LockHolder;
import cn.hg.aifei.lock.internal.LockNames;
import cn.hg.aifei.lock.support.FakeLockStore;
import cn.hg.aifei.lock.support.NoopLogFactory;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 门面与句柄的契约用例：加锁、释放、续期、等待与中断语义全部基于 {@link FakeLockStore}，不连真实存储。
 */
public class LockKitTest {

    /**
     * 测试用锁名。
     */
    private static final String NAME = "order:pay:1";

    /**
     * 测试用租约。
     */
    private static final Duration LEASE = Duration.ofSeconds(30);

    /**
     * 注入的存储替身。
     */
    private FakeLockStore store;

    /**
     * 日志门面依赖已装配的工厂，未装配时取 logger 会失败。
     */
    @BeforeClass
    public static void installLogFactory() {
        LogKit.get().setLogFactory(new NoopLogFactory());
    }

    /**
     * 每个用例注入独立替身。
     */
    @Before
    public void setUp() {
        store = new FakeLockStore();
        LockHolder.register(store);
    }

    /**
     * 用例结束必须清理运行期实例。
     */
    @After
    public void tearDown() {
        LockHolder.clear();
    }

    /**
     * 加锁成功返回绑定锁名的句柄，且存储侧收到非空标识。
     */
    @Test
    public void acquireReturnsHandleBoundToName() {
        Lock lock = LockKit.tryLock(NAME, LEASE);
        assertNotNull(lock);
        assertEquals(NAME, lock.name());
        assertNotNull(store.tokenOf(NAME));
    }

    /**
     * 锁已被他人持有时立即尝试返回 {@code null}，不抛异常。
     */
    @Test
    public void acquireConflictReturnsNull() {
        assertNotNull(LockKit.tryLock(NAME, LEASE));
        assertNull(LockKit.tryLock(NAME, LEASE));
    }

    /**
     * 存储故障按「结果未知」承接：抛 {@link LockException} 而不是返回 {@code null}。
     */
    @Test
    public void acquireFailureThrowsInsteadOfNull() {
        RuntimeException failure = new RuntimeException("sadd failed");
        store.failNextAcquire(failure);

        try {
            LockKit.tryLock(NAME, LEASE);
            fail("存储故障必须抛 LockException");
        } catch (LockException e) {
            assertEquals("lock acquire failed, name: " + NAME, e.getMessage());
            assertSame(failure, e.getCause());
        }
    }

    /**
     * 参数校验先于装配检查，且参数非法不被包装。
     */
    @Test
    public void parameterValidationPrecedesAssembly() {
        LockHolder.clear();
        assertIllegalArgument(() -> LockKit.tryLock(null, LEASE));
        assertIllegalArgument(() -> LockKit.tryLock("", LEASE));
        assertIllegalArgument(() -> LockKit.tryLock("  ", LEASE));
        assertIllegalArgument(() -> LockKit.tryLock("a b", LEASE));
        assertIllegalArgument(() -> LockKit.tryLock(repeat("a", 129), LEASE));
        assertIllegalArgument(() -> LockKit.tryLock(NAME, null));
        assertIllegalArgument(() -> LockKit.tryLock(NAME, Duration.ZERO));
        assertIllegalArgument(() -> LockKit.tryLock(NAME, Duration.ofNanos(999999)));
        assertIllegalArgument(() -> LockKit.tryLock(NAME, LEASE, null));
        assertIllegalArgument(() -> LockKit.tryLock(NAME, LEASE, Duration.ofMillis(-1)));
    }

    /**
     * 未装配时取用抛 {@link IllegalStateException}，不得降级为无锁执行或返回 {@code null}。
     */
    @Test
    public void notInitializedThrows() {
        LockHolder.clear();
        try {
            LockKit.tryLock(NAME, LEASE);
            fail("未装配必须抛 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("has not been initialized"));
        }
    }

    /**
     * 释放幂等：首次成功、第二次返回 {@code false} 且不抛异常，重复关闭同样不抛。
     */
    @Test
    public void releaseIsIdempotent() {
        Lock lock = LockKit.tryLock(NAME, LEASE);
        assertTrue(lock.release());
        assertFalse(lock.release());
        lock.close();
        lock.close();
    }

    /**
     * 误删防护：租约到期并被他人持有后，原句柄释放返回 {@code false} 且不删除新持有者的锁。
     */
    @Test
    public void releaseAfterExpiryKeepsNewHolder() {
        Lock expired = LockKit.tryLock(NAME, LEASE);
        store.expire(NAME);
        Lock current = LockKit.tryLock(NAME, LEASE);
        assertNotNull(current);
        String currentToken = store.tokenOf(NAME);

        assertFalse(expired.release());
        assertEquals("新持有者的锁不得被删除", currentToken, store.tokenOf(NAME));
        assertTrue(current.renew());
    }

    /**
     * 释放异常只表示「是否删除未知」：同一句柄重试安全，且不据此推断锁状态。
     */
    @Test
    public void releaseFailureIsRetryable() {
        Lock lock = LockKit.tryLock(NAME, LEASE);
        store.failNextRelease(new RuntimeException("timeout"));

        try {
            lock.release();
            fail("存储故障必须抛 LockException");
        } catch (LockException e) {
            assertEquals("lock release failed, name: " + NAME, e.getMessage());
            assertNotNull(e.getCause());
        }
        assertTrue(lock.release());
    }

    /**
     * 释放异常后重试得到 {@code false} 同样不算异常：第一次可能已成功删除。
     */
    @Test
    public void releaseFailureFollowedByMissIsNotAnError() {
        Lock lock = LockKit.tryLock(NAME, LEASE);
        store.failNextRelease(new RuntimeException("timeout"));
        try {
            lock.release();
            fail("存储故障必须抛 LockException");
        } catch (LockException expected) {
            assertNotNull("异常不代表已删除，锁仍应在", store.tokenOf(NAME));
        }
        store.expire(NAME);
        assertFalse(lock.release());
    }

    /**
     * 续期使用句柄创建时的租约，而不是调用方另行传入的值。
     */
    @Test
    public void renewUsesHandleLease() {
        Lock lock = LockKit.tryLock(NAME, LEASE);
        assertTrue(lock.renew());
        assertEquals(1, store.renewLeases().size());
        assertEquals(LEASE, store.renewLeases().get(0));
        assertEquals(LEASE, store.leaseOf(NAME));
    }

    /**
     * 锁已丢失时续期返回 {@code false}，且不延长他人的租期。
     */
    @Test
    public void renewFailsWhenLockLost() {
        Lock expired = LockKit.tryLock(NAME, LEASE);
        store.expire(NAME);
        Duration otherLease = Duration.ofSeconds(5);
        Lock current = LockKit.tryLock(NAME, otherLease);
        assertNotNull(current);

        assertFalse(expired.renew());
        assertEquals("不得延长他人租期", otherLease, store.leaseOf(NAME));
    }

    /**
     * 续期异常后重试可成功：异常不表示锁已丢失。
     */
    @Test
    public void renewFailureIsRetryable() {
        Lock lock = LockKit.tryLock(NAME, LEASE);
        store.failNextRenew(new RuntimeException("timeout"));

        try {
            lock.renew();
            fail("存储故障必须抛 LockException");
        } catch (LockException e) {
            assertEquals("lock renew failed, name: " + NAME, e.getMessage());
            assertNotNull(e.getCause());
        }
        assertTrue(lock.renew());
    }

    /**
     * 配对安全：句柄各自绑定锁名，释放一把不影响另一把的续期。
     */
    @Test
    public void handlesArePairedByName() {
        Lock a = LockKit.tryLock("order:a", LEASE);
        Lock b = LockKit.tryLock("order:b", LEASE);
        assertNotNull(a);
        assertNotNull(b);

        assertTrue(a.release());
        assertTrue(b.renew());
    }

    /**
     * 同名两次持有互不相等，句柄采用对象身份比较。
     */
    @Test
    public void handlesUseIdentityEquality() {
        Lock a = LockKit.tryLock(NAME, LEASE);
        store.expire(NAME);
        Lock b = LockKit.tryLock(NAME, LEASE);
        assertNotEquals(a, b);
    }

    /**
     * 等待用尽仍抢不到时返回 {@code null}。
     */
    @Test
    public void waitTimeoutReturnsNull() {
        assertNotNull(LockKit.tryLock(NAME, LEASE));
        long start = System.nanoTime();
        Lock lock = LockKit.tryLock(NAME, LEASE, Duration.ofMillis(60));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertNull(lock);
        assertTrue("等待窗口应被实际消耗，实际 " + elapsedMillis + "ms", elapsedMillis >= 40);
    }

    /**
     * 等待期间被中断：返回 {@code null}、不抛异常，并恢复中断位。
     */
    @Test
    public void interruptDuringWaitReturnsNull() throws InterruptedException {
        assertNotNull(LockKit.tryLock(NAME, LEASE));
        final AtomicReference<Lock> result = new AtomicReference<>();
        final AtomicBoolean interrupted = new AtomicBoolean(false);
        Thread worker = new Thread(() -> {
            result.set(LockKit.tryLock(NAME, LEASE, Duration.ofSeconds(2)));
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        worker.start();
        Thread.sleep(100L);
        worker.interrupt();
        worker.join(3000L);

        assertFalse("线程应已结束", worker.isAlive());
        assertNull(result.get());
        assertTrue("中断位必须被恢复", interrupted.get());
    }

    /**
     * {@code wait == 0} 不检查中断位：对齐非阻塞调用不响应中断的语义。
     */
    @Test
    public void zeroWaitIgnoresInterruptStatus() {
        Thread.currentThread().interrupt();
        try {
            Lock lock = LockKit.tryLock(NAME, LEASE);
            assertNotNull("非阻塞调用不响应中断", lock);
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * 轮询期间存储故障立即上抛，不继续轮询。
     */
    @Test
    public void pollingStopsOnStoreFailure() {
        assertNotNull(LockKit.tryLock(NAME, LEASE));
        RuntimeException failure = new RuntimeException("redis down");
        store.failAcquireOnCall(2, failure);

        try {
            LockKit.tryLock(NAME, LEASE, Duration.ofMillis(500));
            fail("轮询期间存储故障必须立即上抛");
        } catch (LockException e) {
            assertSame(failure, e.getCause());
        }
        assertEquals("不得继续轮询", 2, store.acquireCalls());
    }

    /**
     * {@code close()} 吞掉异常，避免 try-with-resources 抑制业务真实异常。
     */
    @Test
    public void closeSwallowsFailure() {
        Lock lock = LockKit.tryLock(NAME, LEASE);
        store.failNextRelease(new RuntimeException("timeout"));
        lock.close();
    }

    /**
     * 锁分区名须通过 cache 的缓存名校验：不含分段符，且本身是合法锁名。
     */
    @Test
    public void lockPartitionNameIsAcceptedByCache() {
        assertFalse("锁分区名含 ':' 会被 cache 侧 requireCacheName 拒绝",
                LockNames.CACHE_NAME.contains(":"));
        assertEquals(LockNames.CACHE_NAME, LockNames.requireValid(LockNames.CACHE_NAME));
    }

    /**
     * 断言抛出未被包装的 {@link IllegalArgumentException}。
     *
     * @param action 待执行动作
     */
    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    /**
     * 构造指定长度的重复字符串。
     *
     * @param text 重复单元
     * @param times 重复次数
     * @return 结果字符串
     */
    private static String repeat(String text, int times) {
        StringBuilder builder = new StringBuilder(text.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(text);
        }
        return builder.toString();
    }
}
