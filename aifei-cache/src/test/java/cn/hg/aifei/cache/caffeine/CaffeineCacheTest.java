/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.caffeine;

import cn.hg.aifei.cache.internal.Config;
import cn.hg.aifei.cache.internal.UniqueNameAware;
import cn.hg.aifei.cache.TestSupport.FakeTicker;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@link CaffeineCache} 的契约、哨兵与确定性过期用例。
 *
 * <p>可控时钟直接使用包内可见的 {@code (long, Ticker, ...)} 构造，因此不依赖
 * 真实时间等待。</p>
 */
public class CaffeineCacheTest {

    private static final String NS = "aifei";

    private static CaffeineCache newCache(FakeTicker ticker) {
        return new CaffeineCache(1_000L, ticker, NS, Config.DEFAULT_TTL, false);
    }

    private static CaffeineCache newNullCachingCache(FakeTicker ticker) {
        return new CaffeineCache(1_000L, ticker, NS, Config.DEFAULT_TTL, true);
    }

    /**
     * 基本写入与读取。
     */
    @Test
    public void shouldPutAndGetValue() {
        CaffeineCache cache = newCache(new FakeTicker());
        cache.put("user", "1", "tom", Duration.ofSeconds(10));

        assertEquals("tom", cache.get("user", "1"));
        assertTrue(cache.exists("user", "1"));
        assertNull(cache.get("user", "2"));
    }

    /**
     * 剩余有效期随可控时钟递减，到期后判定为不存在。
     */
    @Test
    public void shouldExpireAtExactDeadline() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCache cache = newCache(ticker);
        cache.put("user", "1", "tom", Duration.ofMillis(1_000));

        assertEquals(1_000L, cache.getTtl("user", "1").toMillis());

        ticker.advance(Duration.ofMillis(999));
        assertTrue(cache.exists("user", "1"));
        assertEquals(1L, cache.getTtl("user", "1").toMillis());

        ticker.advance(Duration.ofMillis(1));
        assertFalse(cache.exists("user", "1"));
        assertNull(cache.getTtl("user", "1"));
        assertNull(cache.get("user", "1"));
    }

    /**
     * 键枚举只看当前存储中存在且未过期的条目。
     */
    @Test
    public void shouldExcludeExpiredKeysFromKeys() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCache cache = newCache(ticker);
        cache.put("user", "page:active:queryXxx", "page", Duration.ofSeconds(1));

        assertEquals(Arrays.asList("page:active:queryXxx"), cache.keys("user", 0, 10));

        ticker.advance(Duration.ofSeconds(1));
        assertTrue("过期条目不返回", cache.keys("user", 0, 10).isEmpty());
    }

    /**
     * 回归：已过期条目不得被 {@code expire} 复活。
     */
    @Test
    public void shouldNotResurrectExpiredEntryOnExpire() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCache cache = newCache(ticker);
        cache.put("user", "1", "tom", Duration.ofMillis(500));

        ticker.advance(Duration.ofMillis(500));
        // 另一个读取已把该条目观察为过期
        assertNull(cache.get("user", "1"));

        assertFalse("expire 不得把已被观察为过期的条目复活",
                cache.expire("user", "1", Duration.ofMillis(5_000)));
        assertFalse(cache.exists("user", "1"));
        assertNull(cache.get("user", "1"));

        // 未过期条目可以正常续期
        cache.put("user", "2", "jerry", Duration.ofMillis(500));
        ticker.advance(Duration.ofMillis(100));
        assertTrue(cache.expire("user", "2", Duration.ofMillis(5_000)));
        assertEquals(5_000L, cache.getTtl("user", "2").toMillis());
    }

    /**
     * {@code expire} 不得读取或反序列化 value（此处以哨兵条目验证只动 TTL）。
     */
    @Test
    public void shouldOnlyResetTtlOnExpire() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCache cache = newNullCachingCache(ticker);
        cache.put("user", "1", null, Duration.ofMillis(500));
        assertTrue(cache.exists("user", "1"));
        assertNull(cache.get("user", "1"));

        assertTrue(cache.expire("user", "1", Duration.ofMillis(2_000)));
        assertTrue(cache.exists("user", "1"));
        assertNull(cache.get("user", "1"));
        assertEquals(2_000L, cache.getTtl("user", "1").toMillis());
    }

    /**
     * {@code putIfAbsent} 的命中/未命中行为。
     */
    @Test
    public void shouldPutIfAbsentOnlyWhenMissing() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCache cache = newCache(ticker);

        assertTrue(cache.putIfAbsent("user", "1", "tom", Duration.ofSeconds(10)));
        assertFalse(cache.putIfAbsent("user", "1", "jerry", Duration.ofSeconds(10)));
        assertEquals("tom", cache.get("user", "1"));

        ticker.advance(Duration.ofSeconds(10));
        assertTrue(cache.putIfAbsent("user", "1", "jerry", Duration.ofSeconds(10)));
        assertEquals("jerry", cache.get("user", "1"));
    }

    /**
 * {@code cacheNull = true} 的哨兵契约。
     */
    @Test
    public void shouldHonorNullCachingContract() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCache cache = newNullCachingCache(ticker);

        cache.put("user", "1", null, Duration.ofMillis(1_000));
        assertNull("命中哨兵时 get 返回 null", cache.get("user", "1"));
        assertTrue("命中哨兵时 exists 返回 true", cache.exists("user", "1"));
        assertNotNull("命中哨兵时 getTtl 非 null", cache.getTtl("user", "1"));
        assertFalse("命中哨兵视为已存在", cache.putIfAbsent("user", "1", "x", Duration.ofMillis(1_000)));
        assertNull("未命中的业务键返回 null", cache.get("user", "nothing"));
    }

    /**
     * {@code cacheNull = false} 时写入 {@code null} 不写入且不抛异常，且不覆盖既有值。
     */
    @Test
    public void shouldSkipNullWriteWhenNullCachingDisabled() {
        CaffeineCache cache = newCache(new FakeTicker());

        cache.put("user", "1", null, Duration.ofSeconds(1));
        assertFalse("cacheNull=false 时 put(null) 不得写入", cache.exists("user", "1"));

        cache.put("user", "1", "tom", Duration.ofSeconds(1));
        cache.put("user", "1", null, Duration.ofSeconds(1));
        assertEquals("既有值不得被 null 写入清掉", "tom", cache.get("user", "1"));

        assertFalse(cache.putIfAbsent("user", "2", null, Duration.ofSeconds(1)));
        assertFalse(cache.exists("user", "2"));
    }

    /**
     * {@code clear} 只清空该缓存名下的条目，且只影响本命名空间；前缀相近的其它缓存名不受影响。
     */
    @Test
    public void shouldClearExactlyTheNamedCache() {
        CaffeineCache cache = newCache(new FakeTicker());
        CaffeineCache other = new CaffeineCache(1_000L, new FakeTicker(), "other", Config.DEFAULT_TTL, false);

        cache.put("user", "1", "a", Duration.ofSeconds(10));
        cache.put("user", "sub", "b", Duration.ofSeconds(10));
        cache.put("users", "1", "c", Duration.ofSeconds(10));
        other.put("user", "1", "d", Duration.ofSeconds(10));

        cache.clear("user");

        assertNull(cache.get("user", "1"));
        assertNull("同一缓存名下的其余条目一并清空", cache.get("user", "sub"));
        assertEquals("c", cache.get("users", "1"));
        assertEquals("d", other.get("user", "1"));
    }

    /**
     * 业务键里的冒号不参与分级；缓存名不得含冒号 —— 写路径抛异常，读路径按未命中处理。
     */
    @Test
    public void shouldRejectCacheNameContainingSeparator() {
        CaffeineCache cache = newCache(new FakeTicker());

        cache.put("user", "profile:42", "a", Duration.ofSeconds(10));
        assertEquals("业务键可以含冒号", "a", cache.get("user", "profile:42"));

        cache.clear("user");
        assertNull("同属 user 名下的条目被一并清空", cache.get("user", "profile:42"));

        assertThrows(IllegalArgumentException.class,
                () -> cache.put("user:profile", "42", "b", Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> cache.clear("user:profile"));
        assertNull("读路径不抛异常", cache.get("user:profile", "42"));
    }

    /**
     * {@code clear} 清空该缓存名下的全部条目，并把该缓存名从 {@code cacheNames()} 中精确移除；
     * 未涉及的名称不受影响。
     */
    @Test
    public void shouldRemoveCacheNamesOnClear() {
        CaffeineCache cache = newCache(new FakeTicker());
        cache.put("user", "1", "a", Duration.ofMillis(100));
        cache.put("user", "sub", "b", Duration.ofMillis(100));
        cache.put("order", "1", "c", Duration.ofMillis(100));

        assertEquals(2, cache.cacheNames().size());

        cache.clear("user");
        assertNull("clear 清空该缓存名下的全部条目", cache.get("user", "sub"));
        assertEquals("clear 精确移除该名称，其它名称保留",
                new LinkedHashSet<>(Collections.singletonList("order")), cache.cacheNames());

        cache.clear("order");
        assertTrue("逐个清空全部缓存名后集合必须为空", cache.cacheNames().isEmpty());
    }

    /**
     * {@code remove} 只删单个缓存项，不得改动 {@code cacheNames()}。
     */
    @Test
    public void shouldKeepCacheNamesOnRemove() {
        CaffeineCache cache = newCache(new FakeTicker());
        cache.put("user", "1", "a", Duration.ofSeconds(10));

        cache.remove("user", "1");
        cache.remove("user", "absent");
        cache.remove("never-written", "1");

        assertNull(cache.get("user", "1"));
        assertEquals("remove 不得改动名称集合", Collections.singleton("user"), cache.cacheNames());
    }

    /**
     * 被 {@code clear} 移除的名称在再次写入时重新登记。
     */
    @Test
    public void shouldReregisterCacheNameAfterClear() {
        CaffeineCache cache = newCache(new FakeTicker());
        cache.put("user", "1", "a", Duration.ofSeconds(10));
        cache.clear("user");
        assertTrue(cache.cacheNames().isEmpty());

        cache.put("user", "2", "b", Duration.ofSeconds(10));
        assertEquals("清空后再次写入必须重新登记", Collections.singleton("user"), cache.cacheNames());
    }

    /**
     * 连续清理同一名称是幂等的，第二次是空操作。
     */
    @Test
    public void shouldBeIdempotentOnRepeatedClear() {
        CaffeineCache cache = newCache(new FakeTicker());
        cache.put("user", "1", "a", Duration.ofSeconds(10));

        cache.clear("user");
        cache.clear("user");

        assertTrue(cache.cacheNames().isEmpty());
        assertNull(cache.get("user", "1"));
    }

    /**
     * 普通写入不把保留名引入名称集合（名称集合存于实例字段，与缓存内条目无关）。
     */
    @Test
    public void shouldNotPolluteCacheNamesWithInternalKey() {
        CaffeineCache cache = newCache(new FakeTicker());
        cache.put("user", "1", "a", Duration.ofSeconds(10));
        assertFalse(cache.cacheNames().contains(Config.CACHE_NAMES_NAME));
    }

    /**
     * 保留名作为业务缓存名与普通名称同等对待：可写入、可读、登记进名称集合，且 {@code clear}
     * 真正生效（名称集合已不在缓存内，无需跳过清理）。
     */
    @Test
    public void shouldTreatReservedNameLikeAnyBusinessCacheName() {
        CaffeineCache cache = newCache(new FakeTicker());
        cache.put(Config.CACHE_NAMES_NAME, "1", "v", Duration.ofSeconds(10));

        assertEquals("v", cache.get(Config.CACHE_NAMES_NAME, "1"));
        assertTrue("该名称与其他业务名一样被登记",
                cache.cacheNames().contains(Config.CACHE_NAMES_NAME));

        cache.clear(Config.CACHE_NAMES_NAME);
        assertNull("clear 必须真正生效", cache.get(Config.CACHE_NAMES_NAME, "1"));
        assertTrue("清空后名称集合必须为空", cache.cacheNames().isEmpty());
    }

    /**
     * 名称集合存于实例字段、不参与缓存容量竞争：容量远小于写入量时名称仍完整。
     */
    @Test
    public void shouldKeepCacheNamesIndependentOfCapacity() {
        CaffeineCache cache = new CaffeineCache(1L, new FakeTicker(), NS, Config.DEFAULT_TTL, false);
        for (int i = 0; i < 200; i++) {
            cache.put("user", "k" + i, "v" + i, Duration.ofSeconds(10));
        }
        cache.put("order", "1", "v", Duration.ofSeconds(10));

        assertEquals("容量压力不得影响名称集合",
                new LinkedHashSet<>(Arrays.asList("user", "order")), cache.cacheNames());
    }

    /**
     * 参数非法时不写存储：读路径返回 null / false，{@code remove} 静默，写路径抛异常。
     */
    @Test
    public void shouldTreatInvalidArgumentsAsMiss() {
        CaffeineCache cache = newCache(new FakeTicker());

        assertNull(cache.get(null, "1"));
        assertNull(cache.get("user", " "));
        assertFalse(cache.exists(null, "1"));
        assertNull(cache.getTtl("", "1"));
        cache.remove(null, "1");

        assertThrows(IllegalArgumentException.class,
                () -> cache.put(" ", "1", "v", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> cache.put("user", "1", "v", Duration.ZERO));
        cache.clear("user");
    }

    /**
 * 默认 TTL 与接口常量一致，且实例级配置可读。
     */
    @Test
    public void shouldExposeInstanceConfiguration() {
        CaffeineCache cache = new CaffeineCache(1_000L, new FakeTicker(), "ns2", 12_345L, true);
        assertEquals(Duration.ofMillis(12_345L), cache.ttl());
        assertEquals("ns2", cache.namespace());
        assertTrue(cache.cacheNull());
        assertEquals(Duration.ofMillis(Config.DEFAULT_TTL),
                new CaffeineCache(1_000L).ttl());
    }

    /**
     * 容量与命名空间非法时构造失败。
     *
     * <p>本类<b>不实现</b> {@code AutoCloseable}，因此没有 close 相关用例；条目清理由
     * {@code clear(cacheName)} 承担。</p>
     */
    @Test
    public void shouldValidateConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new CaffeineCache(0L));
        assertThrows(IllegalArgumentException.class,
                () -> new CaffeineCache(1L, null, 1L, false));
    }

    /**
     * 组合键包含 namespace 三个维度，{@code belongsTo} 按「namespace + 缓存名」精确判定。
     */
    @Test
    public void shouldCompareNamespaceInCompositeKey() {
        CaffeineCacheKey one = new CaffeineCacheKey("a", "c", "k");
        CaffeineCacheKey two = new CaffeineCacheKey("b", "c", "k");

        assertFalse(one.equals(two));
        assertTrue(one.belongsTo("a", "c"));
        assertFalse(one.belongsTo("b", "c"));
        // 缓存名必须精确相同：前缀相近不算命中，且方向对称
        assertFalse(new CaffeineCacheKey("a", "cabin", "k").belongsTo("a", "c"));
        assertFalse(one.belongsTo("a", "cabin"));
    }

    /**
     * 进程内缓存不参与启动期排他判定：不声明唯一名能力。
     */
    @Test
    public void shouldNotDeclareUniqueNameCapability() {
        assertFalse(newCache(new FakeTicker()) instanceof UniqueNameAware);
    }
}
