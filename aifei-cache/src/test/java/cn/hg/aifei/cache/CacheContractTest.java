/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache;

import cn.hg.aifei.cache.caffeine.CaffeineCache;
import cn.hg.aifei.cache.internal.AbstractCache;
import cn.hg.aifei.cache.internal.AbstractCounter;
import cn.hg.aifei.cache.internal.Config;
import cn.hg.aifei.cache.redis.RedisCache;
import cn.hg.aifei.cache.redis.RedisConfig;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 与存储无关的契约：哨兵语义、带 {@code loader} 的 {@code get} 校验顺序、
 * TTL 上下限，以及哨兵编解码与物理 key 拼装。
 */
public class CacheContractTest {

    private static Cache newCache(boolean cacheNull) {
        return new CaffeineCache(1_000L, "contract-ns", Config.DEFAULT_TTL, cacheNull);
    }

    /**
     * {@code cacheNull = true} 的哨兵契约。
     */
    @Test
    public void shouldHonorSentinelContractWhenNullCachingEnabled() {
        Cache cache = newCache(true);
        cache.put("user", "1", null, Duration.ofSeconds(30));

        assertNull(cache.get("user", "1"));
        assertTrue(cache.exists("user", "1"));
        assertFalse(cache.putIfAbsent("user", "1", "other", Duration.ofSeconds(30)));
        assertTrue("命中哨兵时 getTtl 必须非 null", cache.getTtl("user", "1") != null);
    }

    /**
     * {@code cacheNull = false} 时写入 {@code null} <b>不写入且不抛异常</b>，且是保持现状而非删除；{@code loader} 返回 {@code null} 同理。
     */
    @Test
    public void shouldSkipNullWriteWhenNullCachingDisabled() {
        Cache cache = newCache(false);

        // 空缓存上 put(null)：不抛异常、不产生条目
        cache.put("user", "1", null, Duration.ofSeconds(30));
        assertFalse("cacheNull=false 时 put(null) 不得写入", cache.exists("user", "1"));

        // 已有值不被覆盖 —— 「不写入」= 保持现状，不是删除
        cache.put("user", "1", "tom", Duration.ofSeconds(30));
        cache.put("user", "1", null, Duration.ofSeconds(30));
        assertEquals("既有值不得被 null 写入清掉", "tom", cache.get("user", "1"));

        // putIfAbsent(null)：同样不写入，并返回 false
        assertFalse("cacheNull=false 时 putIfAbsent(null) 必须返回 false",
                cache.putIfAbsent("user", "2", null, Duration.ofSeconds(30)));
        assertFalse(cache.exists("user", "2"));

        // loader 返回 null：与本条同一条规则（由 toStoredValue 单点判定）
        assertNull(cache.get("user", "3", Duration.ofSeconds(30), () -> null));
        assertFalse("loader 返回 null 且 cacheNull=false 时不得写入", cache.exists("user", "3"));
    }

    /**
     * {@code loader} 返回 {@code null} 且 {@code cacheNull = true} 时写入哨兵。
     */
    @Test
    public void shouldWriteSentinelWhenLoaderReturnsNull() {
        Cache cache = newCache(true);
        assertNull(cache.get("user", "1", Duration.ofSeconds(30), () -> null));
        assertTrue(cache.exists("user", "1"));
        assertNull(cache.get("user", "1"));
    }

    /**
     * 命中时不得调用 {@code loader}；未命中时使用加载值。
     */
    @Test
    public void shouldCallLoaderOnlyOnMiss() {
        Cache cache = newCache(false);
        AtomicInteger calls = new AtomicInteger();

        assertEquals("loaded", cache.get("user", "1", Duration.ofSeconds(30), () -> {
            calls.incrementAndGet();
            return "loaded";
        }));
        assertEquals("loaded", cache.get("user", "1", Duration.ofSeconds(30), () -> {
            calls.incrementAndGet();
            return "ignored";
        }));
        assertEquals("loader 命中时不得被调用", 1, calls.get());
    }

    /**
     * {@code loader} 为 {@code null} 时优先抛，即使 {@code cacheName} / {@code key} / TTL 同时非法也不改变顺序，且不调用 loader。
     */
    @Test
    public void shouldValidateLoaderBeforeOtherArguments() {
        Cache cache = newCache(false);
        Supplier<String> nullLoader = null;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cache.get(null, null, Duration.ZERO, nullLoader));
        assertEquals("loader can not be null", exception.getMessage());

        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class,
                () -> cache.get(" ", " ", Duration.ofSeconds(1), () -> {
                    calls.incrementAndGet();
                    return "x";
                }));
        assertEquals("参数非法时不得调用 loader", 0, calls.get());
    }

    /**
     * TTL 上界在四种写路径上一致可用；{@code Integer.MAX_VALUE} 秒可用，加 1 纳秒被拒。
     */
    @Test
    public void shouldEnforceTtlBoundsConsistently() {
        Cache cache = newCache(false);
        Duration maxTtl = Duration.ofSeconds(Integer.MAX_VALUE);
        Duration overMax = Duration.ofSeconds(Integer.MAX_VALUE).plusNanos(1L);
        Duration overSeconds = Duration.ofSeconds((long) Integer.MAX_VALUE + 1L);

        cache.put("user", "1", "v", maxTtl);
        assertTrue(cache.expire("user", "1", maxTtl));
        assertTrue(cache.putIfAbsent("user", "2", "v", maxTtl));
        assertEquals("v", cache.get("user", "3", maxTtl, () -> "v"));

        assertThrows(IllegalArgumentException.class,
                () -> cache.put("user", "9", "v", overMax));
        assertThrows(IllegalArgumentException.class,
                () -> cache.put("user", "9", "v", overSeconds));
        assertThrows(IllegalArgumentException.class,
                () -> cache.expire("user", "1", overMax));
        assertThrows(IllegalArgumentException.class,
                () -> cache.putIfAbsent("user", "9", "v", overMax));
        assertThrows(IllegalArgumentException.class,
                () -> cache.get("user", "9", overMax, () -> "v"));

        assertThrows(IllegalArgumentException.class,
                () -> cache.put("user", "9", "v", Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> cache.put("user", "9", "v", null));
    }

    /**
     * 秒重载与 {@code Duration} 重载等价。
     */
    @Test
    public void shouldSupportSecondOverloads() {
        Cache cache = newCache(false);
        assertTrue(cache.putIfAbsent("user", "1", "v", 30));
        assertTrue(cache.expire("user", "1", 60));
        assertEquals("v", cache.get("user", "1", 5, () -> "ignored"));
        cache.put("user", "2", "v", 30);
        assertEquals("v", cache.get("user", "2"));

        Counter counter = new CaffeineCache(100L, "contract-ns", Config.DEFAULT_TTL, false).createCounter();
        assertEquals(3L, counter.increase("c", "k", 3L, 30));
        assertEquals(4L, counter.increaseAndRefreshTtl("c", "k", 1L, 30));
        assertEquals(3L, counter.decrease("c", "k", 1L, 30));
        assertEquals(2L, counter.decreaseAndRefreshTtl("c", "k", 1L, 30));
    }

    /**
     * 派生计数器继承同源缓存的默认 TTL，省略 TTL 的重载随之生效。
     */
    @Test
    public void shouldInheritCacheDefaultTtlInDerivedCounter() {
        CaffeineCache cache = new CaffeineCache(100L, "contract-ns", 2_000L, false);
        Counter counter = cache.createCounter();

        assertEquals(Duration.ofMillis(2_000L), counter.ttl());
        assertEquals("派生计数器与同源缓存共享命名空间", cache.namespace(), counter.namespace());

        assertEquals(2L, counter.increase("visits", "u1", 2L));
        assertEquals(5L, counter.increaseAndRefreshTtl("visits", "u1", 3L));
        assertEquals(4L, counter.decrease("visits", "u1", 1L));
        assertEquals(3L, counter.decreaseAndRefreshTtl("visits", "u1", 1L));
        assertEquals(Long.valueOf(3L), counter.get("visits", "u1"));
    }

    /**
     * clear(name) 精确移除整个名称；remove 单键不动名称集合。
     */
    @Test
    public void shouldRemoveCounterNamesOnClear() {
        Counter counter = new CaffeineCache(100L, "contract-ns", 2_000L, false).createCounter();

        assertTrue(counter.counterNames().isEmpty());
        counter.increase("visits", "u1", 1L);
        counter.increase("visits", "u2", 1L);
        assertEquals(new LinkedHashSet<>(Arrays.asList("visits")), counter.counterNames());

        counter.remove("visits", "u1");
        assertEquals("remove 不得改动名称集合",
                new LinkedHashSet<>(Arrays.asList("visits")), counter.counterNames());

        counter.clear("visits");
        assertNull(counter.get("visits", "u1"));
        assertNull("clear 必须清空该名称下的全部计数项", counter.get("visits", "u2"));
        assertTrue("clear 精确移除该名称，集合为空", counter.counterNames().isEmpty());
    }

    /**
     * 名字不得含 {@code :}：写路径抛异常，读路径走「不可能命中」路径；{@code key} 不受此限。
     */
    @Test
    public void shouldRejectNamesContainingSeparator() {
        CaffeineCache cache = new CaffeineCache(100L, "contract-ns", 2_000L, false);
        Duration ttl = Duration.ofSeconds(10);

        assertThrows(IllegalArgumentException.class, () -> cache.put("user:profile", "1", "a", ttl));
        assertThrows(IllegalArgumentException.class, () -> cache.putIfAbsent("user:profile", "1", "a", ttl));
        assertThrows(IllegalArgumentException.class, () -> cache.expire("user:profile", "1", ttl));
        assertThrows(IllegalArgumentException.class, () -> cache.clear("user:profile"));
        assertThrows(IllegalArgumentException.class,
                () -> cache.get("user:profile", "1", ttl, () -> "a"));

        assertNull("读路径不抛异常", cache.get("user:profile", "1"));
        assertFalse(cache.exists("user:profile", "1"));
        assertNull(cache.getTtl("user:profile", "1"));
        cache.remove("user:profile", "1");

        Counter counter = cache.createCounter();
        assertThrows(IllegalArgumentException.class, () -> counter.increase("visits:daily", "u1", 1L, ttl));
        assertThrows(IllegalArgumentException.class, () -> counter.clear("visits:daily"));

        assertNull("读路径不抛异常", counter.get("visits:daily", "u1"));
        counter.remove("visits:daily", "u1");

        cache.put("user", "profile:42", "b", ttl);
        assertEquals("业务键不受冒号限制", "b", cache.get("user", "profile:42"));
    }

    /**
     * 命名空间不得含 {@code :}：它是物理 key 的第一段，含 {@code :} 会让拼接失去单射性，
     * 两个不同实例可落到同一物理 key，而启动期排他判定按「唯一名 + 命名空间」判重、不会报冲突。
     *
     * <p>规则本体在 {@code internal.CacheValidator#requireNamespace}，两个后端的构造期判定都走它
     * —— 绕过配置直接构造实例时唯一的守卫；{@code RedisCache} 的判定发生在建立客户端之前，
     * 因此本用例不需要真实 Redis。空白判定与替换前保持同一异常消息。</p>
     */
    @Test
    public void shouldRejectNamespaceContainingSeparator() {
        assertEquals("namespace can not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> new CaffeineCache(100L, null, 2_000L, false)).getMessage());
        assertEquals("namespace can not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> new CaffeineCache(100L, "  ", 2_000L, false)).getMessage());

        // 含 ':' 即拒，位置不限
        for (String illegal : new String[]{"a:b", ":a", "a:", "a:b:c"}) {
            assertEquals(illegal,
                    "namespace must not contain ':'",
                    assertThrows(IllegalArgumentException.class,
                            () -> new CaffeineCache(100L, illegal, 2_000L, false)).getMessage());
        }

        assertEquals("namespace must not contain ':'",
                assertThrows(IllegalArgumentException.class,
                        () -> new RedisCache(new RedisConfig(), "a:b", 2_000L, false)).getMessage());

        // 只禁 ':'：其余标点不受影响
        assertEquals("a-b_c.d", new CaffeineCache(100L, "a-b_c.d", 2_000L, false).namespace());
    }

    /**
     * {@code internal} 基类上的静态辅助方法：哨兵编解码与物理 key 拼装。
     *
     * <p>不存在 {@code isNullValue} —— 存在性判定一律由存储原语完成（Caffeine 的
     * {@code getIfPresent} / Redis 的 {@code EXISTS}），因此无需把哨兵判定暴露出来。</p>
     */
    @Test
    public void shouldExposeSentinelCodecAndPhysicalKey() {
        assertSame("v", AbstractCache.toStoredValue("v", false));
        Object sentinel = AbstractCache.toStoredValue(null, true);
        assertTrue("cacheNull=true 时 null 必须落成非 null 哨兵", sentinel != null);
        assertSame("哨兵必须是同一单例实例", sentinel, AbstractCache.toStoredValue(null, true));
        assertNull(AbstractCache.fromStoredValue(AbstractCache.toStoredValue(null, true)));
        assertEquals("v", AbstractCache.fromStoredValue("v"));
        // null 且 cacheNull 关闭 → 返回 null，表示「不应写入」（不抛异常）
        assertNull("toStoredValue(null, false) 必须返回 null 表示不写入",
                AbstractCache.toStoredValue(null, false));

        assertEquals("aifei:user:1", AbstractCache.physicalKey("aifei", "user", "1"));
        assertEquals("aifei:COUNTER:visits:1",
                AbstractCounter.physicalKey("aifei", "visits", "1"));
        // 键空间保留前缀与保留名属数据格式：取值在此钉死，改字面量即转红
        assertEquals("COUNTER:", Config.COUNTER_KEY_PREFIX);
        assertEquals("__CACHE_NAMES__", Config.CACHE_NAMES_NAME);
        assertEquals("__COUNTER_NAMES__", Config.COUNTER_NAMES_NAME);
    }

    /**
     * 读路径的空值返回：普通 {@code get} 对非法参数返回 {@code null}（不抛异常）。
     */
    @Test
    public void shouldReturnNullOnInvalidArgumentsForReadPath() {
        Cache cache = newCache(false);
        assertNull(cache.get(null, "1"));
        assertNull(cache.getTtl(" ", "1"));
        assertFalse(cache.exists(null, null));
    }

    /**
     * {@code keys} 先按字典序排序再分页；越界自动截断，空缓存返回空列表。
     */
    @Test
    public void shouldPageKeysByOffsetAndLimit() {
        Cache cache = newCache(false);
        assertTrue("空缓存返回空列表", cache.keys("user", 0, 10).isEmpty());

        for (String key : new String[]{"e", "a", "c", "b", "d"}) {
            cache.put("user", key, "v", Duration.ofSeconds(30));
        }

        assertEquals("排序后全量返回", Arrays.asList("a", "b", "c", "d", "e"), cache.keys("user", 0, 10));
        assertEquals("offset 跳过前一条", Arrays.asList("b", "c", "d", "e"), cache.keys("user", 1, 10));
        assertEquals("limit 截断返回条数", Arrays.asList("a", "b", "c"), cache.keys("user", 0, 3));
        assertEquals("offset 与 limit 组合", Arrays.asList("c", "d"), cache.keys("user", 2, 2));
        assertEquals("limit 超出剩余条数自动截断", Arrays.asList("d", "e"), cache.keys("user", 3, 100));
        assertTrue("offset 越界返回空列表", cache.keys("user", 5, 10).isEmpty());
    }

    /**
     * {@code keys} 的非法分页或匹配类型参数抛 {@code IllegalArgumentException}。
     */
    @Test
    public void shouldRejectInvalidKeysArguments() {
        Cache cache = newCache(false);
        cache.put("user", "1", "v", Duration.ofSeconds(30));

        assertThrows(IllegalArgumentException.class, () -> cache.keys("user", -1, 10));
        assertThrows(IllegalArgumentException.class, () -> cache.keys("user", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> cache.keys("user", 0, -1));
        assertThrows(IllegalArgumentException.class, () -> cache.keys("user", 0, 10, "1", null));
    }

    /**
     * {@code pattern} 按 {@link CacheKeyMatchType} 筛选 key，筛选先于分页；{@code null} 或空串时不筛选。
     */
    @Test
    public void shouldFilterKeysByMatchType() {
        Cache cache = newCache(false);
        for (String key : new String[]{"user:1", "user:2", "order:1", "xuser"}) {
            cache.put("user", key, "v", Duration.ofSeconds(30));
        }

        assertEquals("前缀匹配", Arrays.asList("user:1", "user:2"),
                cache.keys("user", 0, 100, "user:", CacheKeyMatchType.STARTS_WITH));
        assertEquals("后缀匹配", Arrays.asList("order:1", "user:1"),
                cache.keys("user", 0, 100, ":1", CacheKeyMatchType.ENDS_WITH));
        assertEquals("包含匹配", Arrays.asList("user:1", "user:2", "xuser"),
                cache.keys("user", 0, 100, "user", CacheKeyMatchType.CONTAINS));
        assertEquals("完全相等", Arrays.asList("xuser"),
                cache.keys("user", 0, 100, "xuser", CacheKeyMatchType.EQUALS));
        assertEquals("筛选先于分页", Arrays.asList("user:2"),
                cache.keys("user", 1, 1, "user:", CacheKeyMatchType.STARTS_WITH));
        assertEquals("空 pattern 不筛选", Arrays.asList("order:1", "user:1", "user:2", "xuser"),
                cache.keys("user", 0, 100, "", CacheKeyMatchType.EQUALS));
        assertEquals("null pattern 不筛选", Arrays.asList("order:1", "user:1", "user:2", "xuser"),
                cache.keys("user", 0, 100, null, CacheKeyMatchType.CONTAINS));
    }

    /**
     * {@code keys} 只返回该缓存名下的键；{@code clear} 后为空；{@code cacheName} 非法抛异常。
     */
    @Test
    public void shouldScopeKeysToCacheNameAndValidateCacheName() {
        Cache cache = newCache(false);
        cache.put("user", "2", "v", Duration.ofSeconds(30));
        cache.put("user", "1", "v", Duration.ofSeconds(30));
        cache.put("users", "1", "v", Duration.ofSeconds(30));
        cache.put("session", "1", "v", Duration.ofSeconds(30));

        assertEquals(Arrays.asList("1", "2"), cache.keys("user", 0, 100));
        assertEquals("前缀相近的名称不受影响", Arrays.asList("1"), cache.keys("users", 0, 100));

        cache.clear("user");
        assertTrue("clear 后为空", cache.keys("user", 0, 100).isEmpty());

        assertThrows(IllegalArgumentException.class, () -> cache.keys(" ", 0, 100));
        assertThrows(IllegalArgumentException.class, () -> cache.keys("user:x", 0, 100));
    }

    /**
     * 空值项的键同样计入 keys 枚举：只看键存在，不区分哨兵值。
     */
    @Test
    public void shouldIncludeSentinelEntryKeys() {
        Cache cache = newCache(true);
        cache.put("user", "ghost", null, Duration.ofSeconds(30));
        cache.put("user", "real", "v", Duration.ofSeconds(30));

        assertEquals(Arrays.asList("ghost", "real"), cache.keys("user", 0, 100));
    }

    /**
     * pattern 按 glob 精确匹配整个缓存名。
     */
    @Test
    public void shouldMatchGlobCacheNamesExactly() {
        Cache cache = newCache(false);
        cache.put("user*", "1", "v", Duration.ofSeconds(30));
        cache.put("users", "1", "v", Duration.ofSeconds(30));

        assertEquals(Arrays.asList("1"), cache.keys("user*", 0, 100));
        assertEquals(Arrays.asList("1"), cache.keys("users", 0, 100));
    }

    /**
     * 匹配串中的 *、[ 等 glob 字符按字面处理，不当通配符或字符类。
     */
    @Test
    public void shouldTreatGlobCharactersInPatternLiterally() {
        Cache cache = newCache(false);
        for (String key : new String[]{"a*b", "aXb", "[x]", "x"}) {
            cache.put("user", key, "v", Duration.ofSeconds(30));
        }

        assertEquals("包含：* 不当通配符", Arrays.asList("a*b"),
                cache.keys("user", 0, 100, "a*b", CacheKeyMatchType.CONTAINS));
        assertEquals("前缀：[ 不当字符类", Arrays.asList("[x]"),
                cache.keys("user", 0, 100, "[", CacheKeyMatchType.STARTS_WITH));
        assertEquals("完全相等：pattern 含 glob 字符", Arrays.asList("[x]"),
                cache.keys("user", 0, 100, "[x]", CacheKeyMatchType.EQUALS));
    }

    /**
     * 场景回归：{@code key} 含 {@code :} 分级时，{@code put} 写入的 key 经 {@code keys}
     * 按缓存名精确枚举、原样返回并按字典序排序；{@code get} 经同一物理 key 取回。
     */
    @Test
    public void shouldReturnColonSeparatedKeysPutByUser() {
        Cache cache = newCache(false);
        cache.put("user", "page:active:queryXxx", "page", Duration.ofSeconds(30));
        cache.put("user", "page:removed:queryXxx", "page", Duration.ofSeconds(30));

        assertEquals("keys 原样返回 put 写入的 key，字典序排序",
                Arrays.asList("page:active:queryXxx", "page:removed:queryXxx"),
                cache.keys("user", 0, 100));

        assertEquals("put 与 get 经同一物理 key 对应",
                "page", cache.get("user", "page:active:queryXxx"));

        assertTrue("offset 越界返回空列表", cache.keys("user", 5, 10).isEmpty());
        assertTrue("前缀相近的缓存名不受影响", cache.keys("users", 0, 100).isEmpty());
    }
}
