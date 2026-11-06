/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import cn.aifei.log.LogKit;
import cn.hg.aifei.lock.internal.LockHolder;
import cn.hg.aifei.lock.support.FakeLockStore;
import cn.hg.aifei.lock.support.NoopLogFactory;
import java.time.Duration;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 等待窗口的计时用例：对 CI 抖动敏感，默认跳过，本地或离线环境用 {@code -Dlock.timing=true} 执行。
 *
 * <p>容差固定为 35ms，不放大：放大到 100ms 时「睡满剩余等待」的退化实现也能通过。</p>
 */
public class LockTimingOfflineTest {

    /**
     * 开关：默认关闭，不进默认 CI。
     */
    private static final boolean ENABLED = Boolean.getBoolean("lock.timing");

    /**
     * 测试用锁名。
     */
    private static final String NAME = "job:report:offline";

    /**
     * 注入的存储替身。
     */
    private FakeLockStore store;

    /**
     * 日志门面依赖已装配的工厂。
     */
    @BeforeClass
    public static void installLogFactory() {
        LogKit.get().setLogFactory(new NoopLogFactory());
    }

    /**
     * 未开启时跳过整类；注入始终返回「已被持有」的替身。
     */
    @Before
    public void setUp() {
        Assume.assumeTrue("计时用例：用 -Dlock.timing=true 开启", ENABLED);
        store = new FakeLockStore();
        LockHolder.register(store);
        assertNotNull(LockKit.tryLock(NAME, Duration.ofSeconds(30)));
    }

    /**
     * 用例结束必须清理运行期实例。
     */
    @After
    public void tearDown() {
        LockHolder.clear();
    }

    /**
     * 10ms 等待窗口总耗时不超过 35ms：实现必须按剩余等待收窄单次睡眠。
     */
    @Test
    public void waitWindowIsRespected() {
        long start = System.nanoTime();
        Lock lock = LockKit.tryLock(NAME, Duration.ofSeconds(30), Duration.ofMillis(10));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertNull(lock);
        assertTrue("10ms 等待窗口总耗时应不超过 35ms，实际 " + elapsedMillis + "ms", elapsedMillis <= 35);
    }
}
