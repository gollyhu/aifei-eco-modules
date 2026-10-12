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
     * {@code cacheNull = false} 时写入 {@code null} <b>不写入且不抛异常</b>，
     * 且「不写入」是<b>保持现状</b>而非删除；{@code loader} 返回 {@code null} 同理。
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
     * {@code loader} 为 {@code null} 时优先抛 {@code "loader can not be null"}，
     * 即使 {@code cacheName} / {@code key} / TTL 同时非法也不改变顺序，且不调用 loader。
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
     * 职责切分：{@code remove} 只删条目、完全不改动 {@code counterNames()}；{@code clear} 清空该名称
     * 下的全部计数项并精确移除该名称。
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
     * 名字（{@code cacheName} / {@code counterName}）不得含 {@code :}：写路径抛
     * {@code IllegalArgumentException}，读路径走「不可能命中」路径、不抛异常；{@code key} 不受此限。
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
     * 命名空间不得含 {@code :}：它是物理 key 的第一段，含 {@code :} 会让
     * {@code namespace:cacheName:key} 的拼接失去单射性，两个不同实例可落到同一物理 key，
     * 而启动期排他判定按「唯一名 + 命名空间」判重、不会报冲突。
     *
     * <p>规则本体在 {@code internal.CacheValidator#requireNamespace}，两个后端的构造期判定都走它
     * —— 这是绕过配置、直接构造实例时唯一的守卫。{@code RedisCache} 的判定发生在建立客户端之前，
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
     * <p>哨兵约定：{@code null} 在 {@code cacheNull} 关闭时写入失败、开启时落成哨兵，
     * 读取时再透明还原成 {@code null}。不存在 {@code isNullValue} —— 存在性判定一律由
     * 存储原语完成（Caffeine 的 {@code getIfPresent} / Redis 的 {@code EXISTS}），
     * 因此无需把哨兵判定暴露出来。</p>
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
     * {@code keys} 的区间语义对齐 Redis {@code LRANGE}：闭区间含两端、负索引从末尾计数、
     * 越界钳制、{@code begin > end} 或空缓存返回空列表；先按字典序排序再切片。
     */
    @Test
    public void shouldSliceKeysWithLrangeSemantics() {
        Cache cache = newCache(false);
        assertTrue("空缓存返回空列表", cache.keys("user", 0, -1).isEmpty());

        for (String key : new String[]{"e", "a", "c", "b", "d"}) {
            cache.put("user", key, "v", Duration.ofSeconds(30));
        }

        assertEquals(Arrays.asList("a", "b", "c", "d", "e"), cache.keys("user", 0, -1));
        assertEquals("索引 1..3 对应 b..d", Arrays.asList("b", "c", "d"), cache.keys("user", 1, 3));
        assertEquals("begin == end 返回单元素", Arrays.asList("c"), cache.keys("user", 2, 2));
        assertEquals("负索引从末尾计数", Arrays.asList("d", "e"), cache.keys("user", -2, -1));
        assertEquals("end 越界钳制", Arrays.asList("d", "e"), cache.keys("user", 3, 100));
        assertEquals("begin 负索引越界钳制", Arrays.asList("a", "b"), cache.keys("user", -100, 1));
        assertTrue("begin > end 返回空列表", cache.keys("user", 3, 1).isEmpty());
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

        assertEquals(Arrays.asList("1", "2"), cache.keys("user", 0, -1));
        assertEquals("前缀相近的名称不受影响", Arrays.asList("1"), cache.keys("users", 0, -1));

        cache.clear("user");
        assertTrue("clear 后为空", cache.keys("user", 0, -1).isEmpty());

        assertThrows(IllegalArgumentException.class, () -> cache.keys(" ", 0, -1));
        assertThrows(IllegalArgumentException.class, () -> cache.keys("user:x", 0, -1));
    }

    /**
     * {@code cacheNull = true} 写入的哨兵条目，其 key 照常返回：键枚举只看键存在。
     */
    @Test
    public void shouldIncludeSentinelEntryKeys() {
        Cache cache = newCache(true);
        cache.put("user", "ghost", null, Duration.ofSeconds(30));
        cache.put("user", "real", "v", Duration.ofSeconds(30));

        assertEquals(Arrays.asList("ghost", "real"), cache.keys("user", 0, -1));
    }

    /**
     * 含 glob 字符的缓存名经转义后精确匹配，不误伤前缀相近名称。
     */
    @Test
    public void shouldMatchGlobCacheNamesExactly() {
        Cache cache = newCache(false);
        cache.put("user*", "1", "v", Duration.ofSeconds(30));
        cache.put("users", "1", "v", Duration.ofSeconds(30));

        assertEquals(Arrays.asList("1"), cache.keys("user*", 0, -1));
        assertEquals(Arrays.asList("1"), cache.keys("users", 0, -1));
    }

    /**
     * 场景回归：{@code key} 含 {@code :} 分级（{@code page:active} / {@code page:removed}）时，
     * {@code put} 写入的 key 经 {@code keys} 按缓存名精确枚举、原样返回并按字典序排序；
     * 范围超出条目数被钳制；前缀相近的缓存名不受影响；{@code get} 经同一物理 key 取回。
     */
    @Test
    public void shouldReturnColonSeparatedKeysPutByUser() {
        Cache cache = newCache(false);
        cache.put("user", "page:active:queryXxx", "page", Duration.ofSeconds(30));
        cache.put("user", "page:removed:queryXxx", "page", Duration.ofSeconds(30));

        assertEquals("keys 原样返回 put 写入的 key，字典序排序",
                Arrays.asList("page:active:queryXxx", "page:removed:queryXxx"),
                cache.keys("user", 0, 10));

        assertEquals("put 与 get 经同一物理 key 对应",
                "page", cache.get("user", "page:active:queryXxx"));

        assertTrue("范围起点越界返回空列表", cache.keys("user", 5, 9).isEmpty());
        assertTrue("前缀相近的缓存名不受影响", cache.keys("users", 0, 10).isEmpty());
    }
}
