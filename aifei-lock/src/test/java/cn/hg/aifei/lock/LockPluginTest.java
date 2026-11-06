/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.aifei.log.LogKit;
import cn.hg.aifei.cache.internal.CacheManager;
import cn.hg.aifei.cache.internal.Config;
import cn.hg.aifei.lock.aifei.LockPlugin;
import cn.hg.aifei.lock.internal.LockHolder;
import cn.hg.aifei.lock.support.FakeCaches;
import cn.hg.aifei.lock.support.NoopLogFactory;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 装配期校验用例：主缓存实例取用、能力查询、后端不是共享存储与未装配四种失败路径。
 */
public class LockPluginTest {

    /**
     * 日志门面依赖已装配的工厂。
     */
    @BeforeClass
    public static void installLogFactory() {
        LogKit.get().setLogFactory(new NoopLogFactory());
    }

    /**
     * 每个用例从干净的 cache 注册表与未装配状态开始。
     */
    @Before
    public void setUp() {
        CacheManager.clear();
        LockHolder.clear();
    }

    /**
     * 用例结束清理全局状态。
     */
    @After
    public void tearDown() {
        CacheManager.clear();
        LockHolder.clear();
    }

    /**
     * cache 注册表未初始化时启动失败，并保留 cache 侧原因。
     */
    @Test
    public void startFailsWithoutMainCacheInstance() {
        try {
            new LockPlugin().start();
            fail("主缓存实例缺失必须启动失败");
        } catch (IllegalStateException e) {
            assertEquals("cache __MAIN__ instance is not initialized", e.getMessage());
            assertNotNull(e.getCause());
        }
    }

    /**
     * 主缓存实例名为 {@code __MAIN__} 但注册表已初始化、缺少该实例时同样失败。
     */
    @Test
    public void startFailsWhenMainInstanceMissing() {
        CacheManager.register("mem", FakeCaches.capability(true, null));

        try {
            new LockPlugin().start();
            fail("主缓存实例缺失必须启动失败");
        } catch (IllegalStateException e) {
            assertEquals("cache __MAIN__ instance is not initialized", e.getMessage());
            assertNotNull(e.getCause());
        }
    }

    /**
     * 不支持条件操作的后端启动失败：本模块不维护后端白名单，只看能力声明。
     */
    @Test
    public void startFailsForNonSharedBackend() {
        CacheManager.register(Config.MAIN_INSTANCE_NAME, FakeCaches.capability(false, null));

        try {
            new LockPlugin().start();
            fail("caffeine 后端必须启动失败");
        } catch (IllegalStateException e) {
            assertEquals("lock backend is not a shared store", e.getMessage());
        }
    }

    /**
     * 能力查询自身抛异常时按「查询失败」包装，而不是误判为后端不支持。
     */
    @Test
    public void startWrapsCapabilityFailure() {
        RuntimeException failure = new RuntimeException("boom");
        CacheManager.register(Config.MAIN_INSTANCE_NAME, FakeCaches.failingCapability(failure));

        try {
            new LockPlugin().start();
            fail("能力查询失败必须启动失败");
        } catch (IllegalStateException e) {
            assertEquals("lock backend capability check failed", e.getMessage());
            assertSame(failure, e.getCause());
        }
    }

    /**
     * 启动校验只查询一次能力声明，成功后注册运行期实例，停止后取用立即失效。
     */
    @Test
    public void capabilityCheckedOnceAndStoppedClearsHolder() {
        AtomicInteger checks = new AtomicInteger();
        CacheManager.register(Config.MAIN_INSTANCE_NAME, FakeCaches.capability(true, checks));
        LockPlugin plugin = new LockPlugin();

        plugin.start();
        assertEquals(1, checks.get());

        plugin.stop();
        try {
            LockKit.tryLock("order:1", Duration.ofSeconds(30));
            fail("停止后取用必须抛 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("has not been initialized"));
        }
    }
}
