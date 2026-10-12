/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache;

import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.cache.internal.CacheManager;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static cn.hg.aifei.cache.TestSupport.prop;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 多实例专项。
 *
 * <p>同 namespace 互相覆盖只在共享同一 Redis 时才会显现，因此放在
 * Redis 集成用例中验证。</p>
 */
public class MultiInstanceTest {

    private static final String MAIN = "main-ns";

    private static final String MEM = "mem-ns";

    private AopFactory previousFactory;

    private CachePlugin plugin;

    /**
     * 隔离 AOP 单例与注册表。
     */
    @Before
    public void setUp() {
        previousFactory = AopKit.get().getAopFactory();
        AopKit.get().setAopFactory(new AopFactory());
        CacheManager.clear();
    }

    /**
     * 清理插件与注册表。
     */
    @After
    public void tearDown() {
        if (plugin != null) {
            plugin.stop();
        }
        CacheManager.clear();
        AopKit.get().setAopFactory(previousFactory);
    }

    private void startTwoInstances(String extensionNamespace) {
        Prop prop = prop(
                "cache.type", "caffeine",
                "cache.namespace", MAIN,
                "cache.names", "mem",
                "cache.mem.type", "caffeine",
                "cache.mem.namespace", extensionNamespace
        );
        plugin = new CachePlugin(prop);
        plugin.start();
    }

    /**
     * namespace 不同的两个实例，缓存 key 与计数 key 均互不覆盖。
     */
    @Test
    public void shouldIsolateInstancesWithDistinctNamespaces() {
        startTwoInstances(MEM);

        Cache main = CacheUtil.use();
        Cache mem = CacheUtil.use("mem");
        Counter mainCounter = CounterUtil.use();
        Counter memCounter = CounterUtil.use("mem");

        assertNotSame(main, mem);
        assertEquals(MAIN, main.namespace());
        assertEquals(MEM, mem.namespace());

        main.put("user", "1", "main-value", Duration.ofSeconds(30));
        mem.put("user", "1", "mem-value", Duration.ofSeconds(30));
        assertEquals("main-value", main.get("user", "1"));
        assertEquals("mem-value", mem.get("user", "1"));

        assertEquals(5L, mainCounter.increase("visits", "u1", 5L, Duration.ofSeconds(30)));
        assertEquals(3L, memCounter.increase("visits", "u1", 3L, Duration.ofSeconds(30)));
        assertEquals(Long.valueOf(5L), mainCounter.get("visits", "u1"));
        assertEquals(Long.valueOf(3L), memCounter.get("visits", "u1"));
    }

    /**
     * 门面静态方法只作用于主缓存，不与扩展实例串联。
     */
    @Test
    public void shouldScopeStaticFacadeMethodsToMainInstance() {
        startTwoInstances(MEM);

        CacheUtil.put("user", "1", "main-only", Duration.ofSeconds(30));
        assertEquals("main-only", CacheUtil.get("user", "1"));
        assertNull("静态方法写入的条目不得被扩展实例读到",
                CacheUtil.use("mem").get("user", "1"));

        CacheUtil.use("mem").put("user", "1", "mem-only", Duration.ofSeconds(30));
        assertEquals("main-only", CacheUtil.get("user", "1"));
        assertEquals("mem-only", CacheUtil.use("mem").get("user", "1"));

        CounterUtil.increase("visits", "u1", 7L, Duration.ofSeconds(30));
        assertEquals(Long.valueOf(7L), CounterUtil.get("visits", "u1"));
        assertNull(CounterUtil.use("mem").get("visits", "u1"));
    }

    /**
     * {@code clear(cacheName)} 只清空该缓存名下的条目，且不动其它实例。
     */
    @Test
    public void shouldClearCacheNamePerInstance() {
        startTwoInstances(MEM);

        Cache main = CacheUtil.use();
        Cache mem = CacheUtil.use("mem");
        main.put("user", "1", "a", Duration.ofSeconds(30));
        main.put("user", "2", "b", Duration.ofSeconds(30));
        main.put("order", "1", "c", Duration.ofSeconds(30));
        mem.put("user", "1", "d", Duration.ofSeconds(30));

        main.clear("user");

        assertNull(main.get("user", "1"));
        assertNull(main.get("user", "2"));
        assertEquals("c", main.get("order", "1"));
        assertEquals("扩展实例不受主缓存 clear 影响", "d", mem.get("user", "1"));
    }

    /**
     * {@code use()} / {@code use(name)} 在五种情形下的异常类型。
     */
    @Test
    public void shouldReportLookupFailures() {
        // 1) 插件未初始化
        assertThrows(IllegalStateException.class, CacheUtil::use);
        assertThrows(IllegalStateException.class, () -> CacheUtil.use("mem"));
        assertThrows(IllegalStateException.class, CounterUtil::use);
        assertThrows(IllegalStateException.class, () -> CounterUtil.use("mem"));

        // 「先生命周期、后参数」：未初始化 + 非法实例名，仍报生命周期异常
        IllegalStateException lifecycleFirst =
                assertThrows(IllegalStateException.class, () -> CacheUtil.use(null));
        assertTrue(lifecycleFirst.getMessage().contains("has not been initialized"));
        assertThrows(IllegalStateException.class, () -> CacheUtil.use("  "));

        startTwoInstances(MEM);

        // 2) 未知实例名
        IllegalArgumentException unknown =
                assertThrows(IllegalArgumentException.class, () -> CacheUtil.use("ghost"));
        assertTrue(unknown.getMessage().contains("unknown cache instance: ghost"));
        assertThrows(IllegalArgumentException.class, () -> CounterUtil.use("ghost"));

        // 3) null 实例名
        assertThrows(IllegalArgumentException.class, () -> CacheUtil.use(null));
        // 4) 空白实例名
        assertThrows(IllegalArgumentException.class, () -> CacheUtil.use(" "));
        assertThrows(IllegalArgumentException.class, () -> CounterUtil.use("\t"));

        // 5) 已停止
        plugin.stop();
        IllegalStateException stopped =
                assertThrows(IllegalStateException.class, CacheUtil::use);
        assertTrue(stopped.getMessage().contains("has not been initialized"));
        assertThrows(IllegalStateException.class, () -> CacheUtil.use("mem"));
    }

    /**
     * 注册表中的同一实例可被反复取用（引用稳定）。
     */
    @Test
    public void shouldReturnStableReferences() {
        startTwoInstances(MEM);
        assertSame(CacheUtil.use("mem"), CacheUtil.use("mem"));
        assertSame(CounterUtil.use("mem"), CounterUtil.use("mem"));
        assertSame(Aop_cacheSingleton(), CacheUtil.use());
    }

    private static Cache Aop_cacheSingleton() {
        return cn.aifei.aop.Aop.get(Cache.class);
    }
}
