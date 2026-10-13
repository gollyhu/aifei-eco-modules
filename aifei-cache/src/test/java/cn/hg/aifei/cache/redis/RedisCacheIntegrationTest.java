/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.redis;

import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.CacheKeyMatchType;
import cn.hg.aifei.cache.Counter;
import cn.hg.aifei.cache.elementvalue.ElementValue;
import cn.hg.aifei.cache.internal.AbstractCache;
import cn.hg.aifei.cache.internal.AbstractCounter;
import cn.hg.aifei.cache.internal.Config;
import cn.hg.aifei.cache.internal.UniqueNameAware;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Redis 端到端用例，默认跳过；用 {@code -Dredis.integration=true -Dredis.uri=...} 开启。
 *
 * <p>覆盖多实例隔离、命名空间排他、{@code cacheNames()} 的清理移除语义、永久条目 TTL 语义等 Redis 侧行为，
 * 以及 Redis 特有的计数异常语义。全程使用随机前缀，结束时按前缀 {@code SCAN} + {@code UNLINK} 自行清理，
 * 不依赖被测 {@code clear()}。</p>
 */
public class RedisCacheIntegrationTest {

    private static final boolean ENABLED = Boolean.getBoolean("redis.integration");

    private static final String REDIS_URI =
            System.getProperty("redis.uri", "redis://127.0.0.1:6379/0");

    private String prefix;

    private String namespaceA;

    private String namespaceB;

    private RedisCache cacheA;

    private RedisCache cacheB;

    private RedisClient raw;

    /**
     * 未开启时跳过整类；开启后断言连通性，连不上必须失败而不是静默跳过。
     */
    @Before
    public void setUp() {
        Assume.assumeTrue("Redis integration tests: enable -Dredis.integration=true", ENABLED);
        prefix = "aifeihg_" + UUID.randomUUID().toString().replace("-", "");
        namespaceA = prefix + "_a";
        namespaceB = prefix + "_b";

        raw = RedisClient.create(URI.create(REDIS_URI));
        assertEquals("PONG", raw.ping());

        cacheA = new RedisCache(config(), namespaceA, Config.DEFAULT_TTL, false);
        cacheB = new RedisCache(config(), namespaceB, Config.DEFAULT_TTL, false);
    }

    /**
     * 按实例前缀整体清理。计数物理 key 以 namespace 开头、namespace 又以 {@code prefix} 开头，
     * 因此单次 {@code prefix + "*"} 已覆盖缓存与计数两类键。
     */
    @After
    public void tearDown() {
        try {
            if (raw != null) {
                cleanup(prefix + "*");
            }
        } finally {
            closeQuietly(cacheB);
            closeQuietly(cacheA);
            if (raw != null) {
                raw.close();
            }
        }
    }

    private static RedisConfig config() {
        return new RedisConfig().uri(REDIS_URI).maxTotal(8).maxIdle(8)
                .timeoutMillis(2_000).maxWaitMillis(3_000L).jmxEnabled(false);
    }

    private void closeQuietly(RedisCache cache) {
        if (cache != null) {
            cache.close();
        }
    }

    private void cleanup(String pattern) {
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> page = raw.scan(cursor, new ScanParams().match(pattern).count(1_000));
            if (!page.getResult().isEmpty()) {
                raw.unlink(page.getResult().toArray(new String[0]));
            }
            cursor = page.getCursor();
        } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
    }

    /**
     * 数组值经真实 Redis 路径往返：多维数组与业务类型数组。
     *
     * <p>本类默认整类跳过，而数组值的缺陷正是被这道门控放过的；codec 级的形态对照见
     * {@code ArrayValueShapeTest}，本条补上端到端的一层。</p>
     */
    @Test
    public void shouldRoundTripArrayValues() {
        FuryRedisValueCodec.allowPackages(Arrays.asList("cn.hg.aifei.cache.elementvalue"));

        cacheA.put("user", "matrix", new int[][] {{1, 2}, {3, 4}}, Duration.ofSeconds(60));
        int[][] matrix = cacheA.get("user", "matrix");
        assertEquals(2, matrix.length);
        assertEquals(3, matrix[1][0]);

        cacheA.put("user", "table", new String[][] {{"a"}, {"b"}}, Duration.ofSeconds(60));
        String[][] table = cacheA.get("user", "table");
        assertEquals("b", table[1][0]);

        cacheA.put("user", "business", new ElementValue[] {new ElementValue("n", 7)},
                Duration.ofSeconds(60));
        ElementValue[] business = cacheA.get("user", "business");
        assertEquals("n", business[0].name);
        assertEquals(7, business[0].amount);
    }

    /**
     * 基本读写、存在性、剩余有效期与删除。
     */
    @Test
    public void shouldReadWriteAndExpire() {
        cacheA.put("user", "1", "tom", Duration.ofSeconds(60));
        assertEquals("tom", cacheA.get("user", "1"));
        assertTrue(cacheA.exists("user", "1"));

        assertNotNull(cacheA.getTtl("user", "1"));
        assertTrue(cacheA.getTtl("user", "1").toMillis() > 0L);
        assertTrue(cacheA.getTtl("user", "1").toMillis() <= 60_000L);

        assertFalse(cacheA.putIfAbsent("user", "1", "jerry", Duration.ofSeconds(60)));
        assertEquals("tom", cacheA.get("user", "1"));
        assertTrue(cacheA.expire("user", "1", Duration.ofMillis(120_000L)));
        assertTrue(cacheA.getTtl("user", "1").toMillis() > 60_000L);

        cacheA.remove("user", "1");
        assertFalse(cacheA.exists("user", "1"));
        assertNull(cacheA.getTtl("user", "1"));
    }

    /**
     * 哨兵契约在 Redis 侧同样成立。
     */
    @Test
    public void shouldCacheNullWhenEnabled() {
        RedisCache nullCache = new RedisCache(config(), namespaceA, Config.DEFAULT_TTL, true);
        try {
            nullCache.put("user", "1", null, Duration.ofSeconds(60));
            assertNull(nullCache.get("user", "1"));
            assertTrue(nullCache.exists("user", "1"));
            assertNotNull(nullCache.getTtl("user", "1"));
            assertFalse(nullCache.putIfAbsent("user", "1", "x", Duration.ofSeconds(60)));
        } finally {
            nullCache.close();
        }
    }

    /**
     * namespace 不同 → 物理 key 与计数 key 互不覆盖。
     */
    @Test
    public void shouldIsolateNamespaces() {
        cacheA.put("user", "1", "value-a", Duration.ofSeconds(60));
        cacheB.put("user", "1", "value-b", Duration.ofSeconds(60));
        assertEquals("value-a", cacheA.get("user", "1"));
        assertEquals("value-b", cacheB.get("user", "1"));

        Counter counterA = cacheA.createCounter();
        Counter counterB = cacheB.createCounter();
        assertEquals(7L, counterA.increase("visits", "u1", 7L, Duration.ofSeconds(60)));
        assertEquals(2L, counterB.increase("visits", "u1", 2L, Duration.ofSeconds(60)));
        assertEquals(Long.valueOf(7L), counterA.get("visits", "u1"));
        assertEquals(Long.valueOf(2L), counterB.get("visits", "u1"));

        // 物理 key 前缀确认
        assertEquals("value-a", cacheA.get("user", "1"));
        assertEquals(namespaceA + ":" + "user" + ":" + "1", AbstractCache.physicalKey(namespaceA, "user", "1"));
    }

    /**
     * 两个实例 namespace 相同 → 显式断言互相覆盖行为（已接受，不作为通过项）。
     *
     * <p>本用例只负责把「同一 Redis 上 namespace 相同会互相覆盖」这一事实固定下来，避免后续误以为库会替调用方隔离。</p>
     */
    @Test
    public void shouldOverlapWhenNamespaceIdentical() {
        RedisCache peer = new RedisCache(config(), namespaceA, Config.DEFAULT_TTL, false);
        try {
            cacheA.put("user", "1", "from-a", Duration.ofSeconds(60));
            peer.put("user", "1", "from-b", Duration.ofSeconds(60));

            // 已接受：同一 namespace 上后写覆盖前写，双方读到同一个值
            assertEquals("from-b", cacheA.get("user", "1"));
            assertEquals("from-b", peer.get("user", "1"));

            Counter counterA = cacheA.createCounter();
            Counter peerCounter = peer.createCounter();
            assertEquals(5L, counterA.increase("visits", "u1", 5L, Duration.ofSeconds(60)));
            assertEquals(6L, peerCounter.increase("visits", "u1", 1L, Duration.ofSeconds(60)));
            assertEquals(Long.valueOf(6L), counterA.get("visits", "u1"));
        } finally {
            peer.close();
        }
    }

    /**
     * {@code clear(cacheName)} 只清空该缓存名下的条目，且不动其它 namespace。
     */
    @Test
    public void shouldClearCacheNameOnRedis() {
        cacheA.put("user", "1", "a", Duration.ofSeconds(60));
        cacheA.put("user", "2", "b", Duration.ofSeconds(60));
        cacheA.put("users", "1", "c", Duration.ofSeconds(60));
        cacheB.put("user", "1", "d", Duration.ofSeconds(60));

        cacheA.clear("user");

        assertFalse(cacheA.exists("user", "1"));
        assertFalse(cacheA.exists("user", "2"));
        assertTrue(cacheA.exists("users", "1"));
        assertTrue("其它 namespace 不受影响", cacheB.exists("user", "1"));
    }

    /**
     * {@code keys} 的分页语义在 Redis 侧生效：SCAN 收集、字典序排序、分页；glob 字符缓存名转义后不误伤前缀相近名称。
     */
    @Test
    public void shouldPageKeysOnRedis() {
        for (String key : new String[]{"e", "a", "c", "b", "d"}) {
            cacheA.put("user", key, "v", Duration.ofSeconds(60));
        }
        cacheA.put("users", "1", "v", Duration.ofSeconds(60));
        cacheA.put("user*", "1", "v", Duration.ofSeconds(60));

        assertEquals(Arrays.asList("a", "b", "c", "d", "e"), cacheA.keys("user", 0, 100));
        assertEquals("offset 与 limit 组合", Arrays.asList("c", "d"), cacheA.keys("user", 2, 2));
        assertTrue("offset 越界返回空列表", cacheA.keys("user", 5, 10).isEmpty());
        assertEquals("glob 字符缓存名精确匹配", Arrays.asList("1"), cacheA.keys("user*", 0, 100));
        assertEquals("前缀相近的名称不受影响", Arrays.asList("1"), cacheA.keys("users", 0, 100));

        cacheA.clear("user");
        assertTrue("clear 后为空", cacheA.keys("user", 0, 100).isEmpty());
    }

    /**
     * Redis 侧按匹配类型下推 glob 后仍以业务 key 精确筛选：{@code pattern} 只命中物理 key 前缀时不误报。
     */
    @Test
    public void shouldFilterKeysByMatchTypeOnRedis() {
        cacheA.put("user", "1", "v", Duration.ofSeconds(60));
        cacheA.put("user", "2", "v", Duration.ofSeconds(60));
        cacheA.put("user", "other", "v", Duration.ofSeconds(60));

        assertEquals("前缀匹配", Arrays.asList("1"),
                cacheA.keys("user", 0, 100, "1", CacheKeyMatchType.STARTS_WITH));
        assertEquals("后缀匹配", Arrays.asList("other"),
                cacheA.keys("user", 0, 100, "r", CacheKeyMatchType.ENDS_WITH));
        assertTrue("pattern 只命中物理 key 前缀时不误报",
                cacheA.keys("user", 0, 100, "user", CacheKeyMatchType.CONTAINS).isEmpty());
    }

    /**
     * Redis 侧下推 glob 前对 {@code pattern} 中的 glob 特殊字符转义，避免被当作通配符。
     */
    @Test
    public void shouldTreatGlobCharactersInPatternLiterallyOnRedis() {
        cacheA.put("user", "a*b", "v", Duration.ofSeconds(60));
        cacheA.put("user", "aXb", "v", Duration.ofSeconds(60));
        cacheA.put("user", "[x]", "v", Duration.ofSeconds(60));

        assertEquals("包含：* 不当通配符", Arrays.asList("a*b"),
                cacheA.keys("user", 0, 100, "a*b", CacheKeyMatchType.CONTAINS));
        assertEquals("前缀：[ 不当字符类", Arrays.asList("[x]"),
                cacheA.keys("user", 0, 100, "[", CacheKeyMatchType.STARTS_WITH));
    }

    /**
     * {@code key} 含 {@code :} 分级的场景在 Redis 侧同样成立：{@code put} 写入的 key 经 {@code keys} 精确枚举、原样返回，{@code get} 经同一物理 key 取回。
     */
    @Test
    public void shouldReturnColonSeparatedKeysPutByUserOnRedis() {
        cacheA.put("user", "page:active:queryXxx", "page", Duration.ofSeconds(60));
        cacheA.put("user", "page:removed:queryXxx", "page", Duration.ofSeconds(60));

        assertEquals(Arrays.asList("page:active:queryXxx", "page:removed:queryXxx"),
                cacheA.keys("user", 0, 100));
        assertEquals("page", cacheA.get("user", "page:active:queryXxx"));
    }

    /**
     * 名字不得含 {@code :}：写路径抛 {@code IllegalArgumentException}，读路径按未命中处理；{@code key} 不受限。
     */
    @Test
    public void shouldRejectNamesContainingSeparatorOnRedis() {
        Duration ttl = Duration.ofSeconds(60);

        assertThrows(IllegalArgumentException.class, () -> cacheA.put("user:profile", "1", "a", ttl));
        assertThrows(IllegalArgumentException.class, () -> cacheA.putIfAbsent("user:profile", "1", "a", ttl));
        assertThrows(IllegalArgumentException.class, () -> cacheA.expire("user:profile", "1", ttl));
        assertThrows(IllegalArgumentException.class, () -> cacheA.clear("user:profile"));

        assertNull("读路径不抛异常", cacheA.get("user:profile", "1"));
        assertFalse(cacheA.exists("user:profile", "1"));
        assertNull(cacheA.getTtl("user:profile", "1"));
        cacheA.remove("user:profile", "1");

        Counter counter = cacheA.createCounter();
        assertThrows(IllegalArgumentException.class,
                () -> counter.increase("visits:daily", "u1", 1L, ttl));
        assertThrows(IllegalArgumentException.class, () -> counter.clear("visits:daily"));
        assertNull("读路径不抛异常", counter.get("visits:daily", "u1"));

        cacheA.put("user", "profile:42", "b", ttl);
        assertEquals("业务键不受冒号限制", "b", cacheA.get("user", "profile:42"));
    }

    /**
     * （Redis 侧）{@code clear} 精确移除被清空的缓存名、跨实例可见；{@code remove} 完全不改动名称集合。
     */
    @Test
    public void shouldShareCacheNamesAcrossInstances() {
        cacheA.put("user", "1", "a", Duration.ofSeconds(60));
        cacheA.put("user", "2", "b", Duration.ofSeconds(60));
        cacheA.put("order", "1", "c", Duration.ofSeconds(60));

        Set<String> names = cacheA.cacheNames();
        assertTrue(names.contains("user"));
        assertTrue(names.contains("order"));

        cacheA.remove("user", "1");
        assertTrue("remove 不得改动名称集合", cacheA.cacheNames().contains("user"));

        cacheA.clear("user");
        assertFalse("clear 后该缓存名必须移除", cacheA.cacheNames().contains("user"));
        assertFalse("clear 清空该名称下的全部条目", cacheA.exists("user", "2"));
        assertTrue(cacheA.cacheNames().contains("order"));

        // 同 namespace 的另一个实例能看到同一集合，包含 clear 造成的移除
        RedisCache peer = new RedisCache(config(), namespaceA, Config.DEFAULT_TTL, false);
        try {
            assertTrue(peer.cacheNames().contains("order"));
            assertFalse("移除对同 namespace 的其它实例同样可见", peer.cacheNames().contains("user"));
        } finally {
            peer.close();
        }

        // 不同 namespace 的实例集合互相隔离
        assertFalse(cacheB.cacheNames().contains("order"));
    }

    /**
     * （Redis 侧）{@code counterNames()} 跨实例共享；{@code remove} 不改动名称集合，{@code clear} 清空该名称下的全部计数项并精确移除该名称。
     */
    @Test
    public void shouldShareCounterNamesAcrossInstances() {
        Counter counterA = cacheA.createCounter();
        assertEquals("派生计数器继承同源缓存的默认 TTL",
                Duration.ofMillis(Config.DEFAULT_TTL), counterA.ttl());
        assertEquals(1L, counterA.increase("visits", "u1", 1L, Duration.ofSeconds(60)));
        counterA.increase("visits", "u2", 1L, Duration.ofSeconds(60));
        assertEquals(new LinkedHashSet<>(Arrays.asList("visits")), counterA.counterNames());

        counterA.remove("visits", "u1");
        assertEquals("remove 不得改动名称集合",
                new LinkedHashSet<>(Arrays.asList("visits")), counterA.counterNames());

        // 名称集合与计数数值都在 Redis 上，同 namespace 的另一个实例能看到
        RedisCache peer = new RedisCache(config(), namespaceA, Config.DEFAULT_TTL, false);
        try {
            Counter peerCounter = peer.createCounter();
            assertTrue(peerCounter.counterNames().contains("visits"));

            counterA.clear("visits");
            assertNull(counterA.get("visits", "u1"));
            assertNull("clear 必须清空该名称下的全部计数项", counterA.get("visits", "u2"));
            assertTrue("clear 精确移除该名称", peerCounter.counterNames().isEmpty());

            // 不同 namespace 的名称集合互相隔离
            assertFalse(cacheB.createCounter().counterNames().contains("visits"));
        } finally {
            peer.close();
        }
    }

    /**
     * （Redis 侧）省略 TTL 的重载使用实例默认 TTL，并保持固定窗口语义。
     */
    @Test
    public void shouldUseInstanceDefaultTtlForTtlOmittingOverloads() {
        String namespace = namespaceA + "_ttl";
        RedisCache shortTtl = new RedisCache(config(), namespace, 3_000L, false);
        try {
            Counter counter = shortTtl.createCounter();
            assertEquals(Duration.ofMillis(3_000L), counter.ttl());

            assertEquals(1L, counter.increase("visits", "u1", 1L));
            assertEquals(2L, counter.increaseAndRefreshTtl("visits", "u1", 1L));
            assertEquals(1L, counter.decrease("visits", "u1", 1L));
            assertEquals(0L, counter.decreaseAndRefreshTtl("visits", "u1", 1L));

            long pttl = raw.pttl(AbstractCounter.physicalKey(namespace, "visits", "u1"));
            assertTrue("省略 TTL 的写路径必须使用实例默认 TTL，实际 pttl=" + pttl,
                    pttl > 0L && pttl <= 3_000L);
        } finally {
            shortTtl.close();
        }
    }

    /**
     * 外部直写的永久条目（{@code PTTL == -1}）返回 {@code Integer.MAX_VALUE} 秒。
     */
    @Test
    public void shouldReportMaxDurationForPermanentEntry() {
        String key = AbstractCache.physicalKey(namespaceA, "user", "1");
        raw.set(key, "raw-value");

        assertEquals(Duration.ofSeconds(Integer.MAX_VALUE), cacheA.getTtl("user", "1"));
        assertTrue(cacheA.exists("user", "1"));
    }

    /**
     * {@code cache.jedis.uri} 与 {@code cache.jedis.host} / {@code cache.jedis.port} 同时配置时 URI 优先。
     *
     * <p>把 host / port 指向一个不可能监听的端口；若 URI 未优先生效，{@code ping} 必然失败。</p>
     */
    @Test
    public void shouldPreferUriOverHostAndPort() {
        RedisConfig preferUri = new RedisConfig()
                .uri(REDIS_URI)
                .host("127.0.0.1")
                .port(1);
        RedisClient client = preferUri.createClient();
        try {
            assertEquals("PONG", client.ping());
        } finally {
            client.close();
        }
    }

    /**
     * Redis 计数的固定窗口、刷新 TTL 与删除后重启。
     */
    @Test
    public void shouldKeepCounterSemanticsOnRedis() {
        Counter counter = cacheA.createCounter();
        assertNull(counter.get("visits", "u1"));

        String key = AbstractCounter.physicalKey(namespaceA, "visits", "u1");
        assertEquals("计数 key 必须使用保留前缀",
                Config.COUNTER_KEY_PREFIX + "visits:u1",
                key.substring(namespaceA.length() + 1));

        assertEquals(1L, counter.increase("visits", "u1", 1L, Duration.ofSeconds(60)));

        // 固定窗口：第二次以 1 秒 TTL 命中，不得把剩余 TTL 重置为 1 秒
        assertEquals(2L, counter.increase("visits", "u1", 1L, Duration.ofSeconds(1)));
        assertTrue("固定窗口命中不得重置截止点", raw.pttl(key) > 1_000L);

        counter.remove("visits", "u1");
        assertNull(counter.get("visits", "u1"));
        assertEquals("删除后必须从 0 重新开始", 1L,
                counter.increase("visits", "u1", 1L, Duration.ofSeconds(60)));
    }

    /**
     * Redis 计数异常语义：永久计数项（无 TTL）非法，溢出转 {@code ArithmeticException}。
     */
    @Test
    public void shouldTranslateCounterFailures() {
        Counter counter = cacheA.createCounter();
        String key = AbstractCounter.physicalKey(namespaceA, "visits", "u1");

        raw.set(key, "5");
        assertThrows(IllegalStateException.class, () -> counter.get("visits", "u1"));

        raw.set(key, "not-a-number", SetParams.setParams().px(60_000L));
        assertThrows(IllegalStateException.class, () -> counter.get("visits", "u1"));

        raw.set(key, String.valueOf(Long.MAX_VALUE), SetParams.setParams().px(60_000L));
        assertThrows(ArithmeticException.class,
                () -> counter.increase("visits", "u1", 1L, Duration.ofSeconds(60)));
    }

    /**
     * RESP3 协议下的基本读写与计数。
     *
     * <p>RESP3 需要服务端支持 {@code HELLO}（Redis 6+）。服务端过旧时本用例跳过，并在跳过理由中
     * 说明服务器能力缺失 —— 属环境限制，不是被测缺陷。新版 Jedis 在握手阶段会把该错误包装成
     * {@code JedisProtocolNotSupportedException}，因此须沿异常链判定。</p>
     */
    @Test
    public void shouldWorkWithResp3() {
        RedisCache resp3;
        try {
            resp3 = new RedisCache(config().resp3(), namespaceA, Config.DEFAULT_TTL, false);
            resp3.put("user", "1", "tom", Duration.ofSeconds(60));
        } catch (JedisException e) {
            Assume.assumeTrue("Redis server does not support RESP3 (HELLO): " + e,
                    !mentionsHello(e));
            throw e;
        }
        try {
            assertEquals("tom", resp3.get("user", "1"));
            assertEquals(2L, resp3.createCounter().increase("visits", "u1", 2L, Duration.ofSeconds(60)));
            assertTrue(resp3.cacheNames().contains("user"));
        } finally {
            resp3.close();
        }
    }

    /**
     * 异常链上任一消息涉及 {@code HELLO}，即视为服务端不支持 RESP3 的环境限制。
     */
    private static boolean mentionsHello(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (String.valueOf(t.getMessage()).contains("HELLO")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 唯一名来自 Redis 连接配置（{@code host:port/database}），用于启动期排他判定。
     */
    @Test
    public void shouldExposeUniqueNameFromConfig() {
        RedisConfig cfg = config();
        RedisCache cache = new RedisCache(cfg, namespaceA, Config.DEFAULT_TTL, false);
        try {
            assertTrue(cache instanceof UniqueNameAware);
            assertEquals(cfg.uniqueName(), cache.uniqueName());
        } finally {
            cache.close();
        }
    }
}
