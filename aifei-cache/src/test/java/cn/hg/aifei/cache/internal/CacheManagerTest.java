/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.CacheKeyMatchType;
import cn.hg.aifei.cache.Counter;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@link CacheManager} 的注册期校验、取用期判定顺序与「无遍历入口」用例。
 */
public class CacheManagerTest {

    /**
     * 每个用例前后都清空注册表。
     */
    @Before
    public void clearBefore() {
        CacheManager.clear();
    }

    /**
     * 用例结束后清空注册表。
     */
    @After
    public void clearAfter() {
        CacheManager.clear();
    }

    /**
     * 注册期参数与实现类型校验。
     */
    @Test
    public void shouldRejectInvalidRegistration() {
        assertThrows(IllegalArgumentException.class, () -> CacheManager.register(null, new StubCache()));
        assertThrows(IllegalArgumentException.class, () -> CacheManager.register("  ", new StubCache()));
        assertThrows(IllegalArgumentException.class, () -> CacheManager.register("mem", null));

        UnsupportedCache unsupported = new UnsupportedCache();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> CacheManager.register("mem", unsupported));
        assertEquals("Unsupported cache implementation for automatic counter creation: "
                + UnsupportedCache.class.getName(), exception.getMessage());
    }

    /**
     * 同名重复注册抛 {@code IllegalStateException}。
     */
    @Test
    public void shouldRejectDuplicateInstanceName() {
        CacheManager.register("mem", new StubCache());
        assertThrows(IllegalStateException.class, () -> CacheManager.register("mem", new StubCache()));
    }

    /**
     * 注册表不提供遍历入口。
     */
    @Test
    public void shouldNotExposeTraversalEntryPoints() {
        Set<String> forbidden = new java.util.HashSet<>(
                java.util.Arrays.asList("names", "entrySet", "values", "keySet", "iterator"));
        for (Method method : CacheManager.class.getDeclaredMethods()) {
            assertFalse("CacheManager 不得暴露遍历入口: " + method.getName(),
                    forbidden.contains(method.getName()));
        }
    }

    /**
     * 取用期「先生命周期、后参数」。
     */
    @Test
    public void shouldCheckLifecycleBeforeArguments() {
        // 未初始化时，即使实例名同样非法，也必须先报告生命周期问题
        IllegalStateException nullName =
                assertThrows(IllegalStateException.class, () -> CacheManager.getCache(null));
        assertTrue(nullName.getMessage().contains("has not been initialized"));

        IllegalStateException unknown =
                assertThrows(IllegalStateException.class, () -> CacheManager.getCounter("mem"));
        assertTrue(unknown.getMessage().contains("has not been initialized"));

        // 初始化后，实例名校验与未知实例判定才生效
        CacheManager.register("mem", new StubCache());
        assertThrows(IllegalArgumentException.class, () -> CacheManager.getCache(null));
        IllegalArgumentException unknownInstance =
                assertThrows(IllegalArgumentException.class, () -> CacheManager.getCache("other"));
        assertTrue(unknownInstance.getMessage().contains("unknown cache instance: other"));
        assertTrue(unknownInstance.getMessage().contains("mem"));
    }

    /**
     * {@code clear()} 之后取用入口立即失效。
     */
    @Test
    public void shouldInvalidateLookupAfterClear() {
        CacheManager.register("mem", new StubCache());
        assertSame(CacheManager.getCache("mem"), CacheManager.getCache("mem"));

        CacheManager.clear();
        assertThrows(IllegalStateException.class, () -> CacheManager.getCache("mem"));
    }

    /**
     * {@code getCache} 与 {@code getCounter} 是同源配对，且返回稳定对象。
     */
    @Test
    public void shouldPairCacheWithDerivedCounter() {
        StubCache cache = new StubCache();
        CacheManager.register("mem", cache);

        assertSame(cache, CacheManager.getCache("mem"));
        assertSame(cache, CacheManager.getCache("mem"));
        assertSame(cache.createdCounter, CacheManager.getCounter("mem"));
        assertSame(CacheManager.getCounter("mem"), CacheManager.getCounter("mem"));
    }

    /**
     * {@code createCounter()} 返回 {@code null} 时拒绝注册。
     */
    @Test
    public void shouldRejectNullCounter() {
        assertThrows(IllegalArgumentException.class, () -> CacheManager.register("mem", new NullCounterCache()));
    }

    // ─────────── 唯一名 + namespace 排他───────────

    /**
     * 同一存储 + 同一 namespace → 拒绝，消息列出冲突的两个实例名。
     */
    @Test
    public void shouldRejectDuplicateKeySpace() {
        CacheManager.register("first", new IdentityCache("127.0.0.1:6379/0", "aifei"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> CacheManager.register("second", new IdentityCache("127.0.0.1:6379/0", "aifei")));

        assertTrue(exception.getMessage().contains("duplicate key space"));
        assertTrue(exception.getMessage().contains("first"));
        assertTrue(exception.getMessage().contains("second"));
        assertTrue(exception.getMessage().contains("127.0.0.1:6379/0"));
    }

    /**
     * 同一存储但 namespace 不同 → 放行。
     */
    @Test
    public void shouldAllowSameStorageWithDistinctNamespace() {
        CacheManager.register("first", new IdentityCache("127.0.0.1:6379/0", "ns-a"));
        CacheManager.register("second", new IdentityCache("127.0.0.1:6379/0", "ns-b"));

        assertSame(CacheManager.getCache("second"), CacheManager.getCache("second"));
    }

    /**
     * <b>不同存储</b>但 namespace 相同 → 放行（"两个 jedis 指向不同 Redis、都未配 namespace"的常见合法配置）。
     */
    @Test
    public void shouldAllowDistinctStorageWithSameNamespace() {
        CacheManager.register("first", new IdentityCache("10.0.0.1:6379/0", "aifei"));
        CacheManager.register("second", new IdentityCache("10.0.0.2:6379/0", "aifei"));

        assertSame(CacheManager.getCache("first"), CacheManager.getCache("first"));
    }

    /**
     * 未实现 {@code UniqueNameAware} 的实例（进程内后端）不参与排他 —— 同 namespace 也放行。
     */
    @Test
    public void shouldNotApplyExclusivityToLocalBackends() {
        CacheManager.register("first", new StubCache());
        CacheManager.register("second", new StubCache());

        assertSame(CacheManager.getCache("second"), CacheManager.getCache("second"));
    }

    /**
     * 实现了 {@code UniqueNameAware} 但唯一名为 {@code null} 时<b>不参与</b>排他判定 —— 两个这样的
     * 实例同 namespace 也放行。
     */
    /**
     * 注册表内已存在<b>不参与排他</b>的实例（进程内后端）时，可参与排他的实例仍能注册 ——
     * 遍历既有条目必须按「是否实现 {@code UniqueNameAware}」跳过，不得直接转型。
     */
    @Test
    public void shouldSkipNonParticipatingInstancesAlreadyRegistered() {
        CacheManager.register("mem", new StubCache());
        CacheManager.register("remote",
                new IdentityCache("127.0.0.1:6379/0", Config.DEFAULT_NAMESPACE));

        assertSame(CacheManager.getCache("remote"), CacheManager.getCache("remote"));
    }

    @Test
    public void shouldTreatNullUniqueNameAsNotParticipating() {
        CacheManager.register("first", new IdentityCache(null, "aifei"));
        CacheManager.register("second", new IdentityCache(null, "aifei"));

        assertSame(CacheManager.getCache("second"), CacheManager.getCache("second"));
    }

    /**
     * 排他判定失败时不得留下部分注册结果（注册表保持原状）。
     */
    @Test
    public void shouldNotPartiallyRegisterOnDuplicateKeySpace() {
        CacheManager.register("first", new IdentityCache("127.0.0.1:6379/0", "aifei"));
        assertThrows(IllegalArgumentException.class,
                () -> CacheManager.register("second", new IdentityCache("127.0.0.1:6379/0", "aifei")));

        assertThrows(IllegalArgumentException.class, () -> CacheManager.getCache("second"));
        assertSame(CacheManager.getCache("first"), CacheManager.getCache("first"));
    }

    /**
     * 声明唯一名能力的测试缓存桩；{@code uniqueName} 为 {@code null} 表示不参与排他。
     */
    static final class IdentityCache extends StubCache implements UniqueNameAware {

        private final String uniqueName;

        private final String namespace;

        IdentityCache(String uniqueName, String namespace) {
            this.uniqueName = uniqueName;
            this.namespace = namespace;
        }

        @Override
        public String uniqueName() {
            return uniqueName;
        }

        @Override
        public String namespace() {
            return namespace;
        }
    }

    /**
     * 可派生计数器的测试缓存桩。
     */
    static class StubCache implements Cache, CounterFactory {

        Counter createdCounter;

        @Override
        public Counter createCounter() {
            createdCounter = new StubCounter();
            return createdCounter;
        }

        @Override
        public <T> T get(String cacheName, String key) {
            return null;
        }

        @Override
        public boolean exists(String cacheName, String key) {
            return false;
        }

        @Override
        public void put(String cacheName, String key, Object value, Duration ttl) {
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
        }

        @Override
        public void clear(String cacheName) {
        }

        @Override
        public Duration getTtl(String cacheName, String key) {
            return null;
        }

        @Override
        public List<String> keys(String cacheName, int offset, int limit, String pattern,
                                 CacheKeyMatchType matchType) {
            return Collections.emptyList();
        }

        @Override
        public Set<String> cacheNames() {
            return Collections.emptySet();
        }

        @Override
        public Duration ttl() {
            return Duration.ofMillis(Config.DEFAULT_TTL);
        }

        @Override
        public boolean cacheNull() {
            return false;
        }

        @Override
        public String namespace() {
            return Config.DEFAULT_NAMESPACE;
        }
    }

    /**
     * 不实现 {@link CounterFactory} 的缓存桩。
     */
    static final class UnsupportedCache implements Cache {

        @Override
        public <T> T get(String cacheName, String key) {
            return null;
        }

        @Override
        public boolean exists(String cacheName, String key) {
            return false;
        }

        @Override
        public void put(String cacheName, String key, Object value, Duration ttl) {
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
        }

        @Override
        public void clear(String cacheName) {
        }

        @Override
        public Duration getTtl(String cacheName, String key) {
            return null;
        }

        @Override
        public List<String> keys(String cacheName, int offset, int limit, String pattern,
                                 CacheKeyMatchType matchType) {
            return Collections.emptyList();
        }

        @Override
        public Set<String> cacheNames() {
            return Collections.emptySet();
        }

        @Override
        public Duration ttl() {
            return Duration.ofMillis(Config.DEFAULT_TTL);
        }

        @Override
        public boolean cacheNull() {
            return false;
        }

        @Override
        public String namespace() {
            return Config.DEFAULT_NAMESPACE;
        }
    }

    /**
     * 派生计数器返回 {@code null} 的缓存桩。
     */
    static final class NullCounterCache extends StubCache {

        @Override
        public Counter createCounter() {
            return null;
        }
    }

    /**
     * 计数桩。
     */
    static final class StubCounter implements Counter {

        @Override
        public Long get(String counterName, String key) {
            return null;
        }

        @Override
        public long increase(String counterName, String key, long step, Duration ttl) {
            return step;
        }

        @Override
        public long increaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
            return step;
        }

        @Override
        public long decrease(String counterName, String key, long step, Duration ttl) {
            return -step;
        }

        @Override
        public long decreaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
            return -step;
        }

        @Override
        public void remove(String counterName, String key) {
        }

        @Override
        public void clear(String counterName) {
        }

        @Override
        public Set<String> counterNames() {
            return Collections.emptySet();
        }

        @Override
        public Duration ttl() {
            return Duration.ofMillis(Config.DEFAULT_TTL);
        }

        @Override
        public String namespace() {
            return Config.DEFAULT_NAMESPACE;
        }
    }
}
