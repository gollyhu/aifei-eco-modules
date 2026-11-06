/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock.cache;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import cn.aifei.log.LogKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.CacheKit;
import cn.hg.aifei.cache.CachePlugin;
import cn.hg.aifei.lock.Lock;
import cn.hg.aifei.lock.LockKit;
import cn.hg.aifei.lock.aifei.LockPlugin;
import cn.hg.aifei.lock.internal.LockHolder;
import cn.hg.aifei.lock.internal.LockNames;
import cn.hg.aifei.lock.support.NoopLogFactory;
import java.time.Duration;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Redis 端到端用例，默认跳过；用 {@code -Dredis.integration=true} 开启，连接地址取
 * {@code -Dredis.uri}（缺省 {@code redis://127.0.0.1:6379/0}）。
 *
 * <p>开启后连不上 Redis 必须失败，不得静默跳过：启动期的探活写入会直接抛异常。装配只在类级别执行一次
 * （框架的 {@code @Inject} 单例不允许重复注册），每个用例前清空锁分区。</p>
 */
public class CacheLockStoreIntegrationTest {

    /**
     * 集成用例开关。
     */
    private static final boolean ENABLED = Boolean.getBoolean("redis.integration");

    /**
     * Redis 连接地址。
     */
    private static final String REDIS_URI =
            System.getProperty("redis.uri", "redis://127.0.0.1:6379/0");

    /**
     * 测试命名空间，避免与其它应用互相覆盖。
     */
    private static final String NAMESPACE = "aifeilock_it";

    /**
     * 测试用锁名。
     */
    private static final String NAME = "it:order:1";

    /**
     * 缓存插件。
     */
    private static CachePlugin cachePlugin;

    /**
     * 主缓存实例。
     */
    private static Cache cache;

    /**
     * 未开启时跳过整类；开启后装配 cache 与 lock，并立即探活。
     */
    @BeforeClass
    public static void setUpClass() {
        Assume.assumeTrue("Redis integration tests: enable -Dredis.integration=true", ENABLED);
        LogKit.get().setLogFactory(new NoopLogFactory());

        cachePlugin = new CachePlugin(prop());
        cachePlugin.start();
        cache = CacheKit.use();
        cache.put(LockNames.CACHE_NAME, "it:probe", "probe", Duration.ofSeconds(5));

        new LockPlugin().start();
    }

    /**
     * 清空锁分区并关闭缓存插件。
     */
    @AfterClass
    public static void tearDownClass() {
        LockHolder.clear();
        if (cachePlugin != null) {
            try {
                if (cache != null) {
                    cache.clear(LockNames.CACHE_NAME);
                }
            } finally {
                cachePlugin.stop();
            }
        }
    }

    /**
     * 用例之间不得残留锁键。
     */
    @Before
    public void clearLocks() {
        cache.clear(LockNames.CACHE_NAME);
    }

    /**
     * 加锁成功即带租约：一条命令写入值与有效期。
     */
    @Test
    public void acquireCarriesLease() {
        Lock lock = LockKit.tryLock(NAME, Duration.ofSeconds(30));
        assertNotNull(lock);

        Duration ttl = cache.getTtl(LockNames.CACHE_NAME, NAME);
        assertNotNull("租约与加锁须为同一条命令", ttl);
        assertTrue(ttl.toMillis() > 0);
        assertTrue(lock.release());
    }

    /**
     * 互斥：同一锁名只有一方能取到，释放后可再次获取。
     */
    @Test
    public void mutualExclusion() {
        Lock first = LockKit.tryLock(NAME, Duration.ofSeconds(30));
        assertNotNull(first);
        assertNull(LockKit.tryLock(NAME, Duration.ofSeconds(30)));

        assertTrue(first.release());
        Lock second = LockKit.tryLock(NAME, Duration.ofSeconds(30));
        assertNotNull(second);
        assertTrue(second.release());
    }

    /**
     * 误删防护：租约到期被他人接管后，原句柄释放失败且不删除新持有者的锁。
     */
    @Test
    public void expiryAllowsTakeOverWithoutDeletingNewHolder() throws InterruptedException {
        Lock expired = LockKit.tryLock(NAME, Duration.ofMillis(200));
        assertNotNull(expired);
        Thread.sleep(300L);

        Lock current = LockKit.tryLock(NAME, Duration.ofSeconds(30));
        assertNotNull(current);

        assertFalse(expired.release());
        Duration ttl = cache.getTtl(LockNames.CACHE_NAME, NAME);
        assertNotNull("新持有者的锁不得被删除", ttl);
        assertTrue(current.release());
    }

    /**
     * 续期成功后有效期回到创建时的租约。
     */
    @Test
    public void renewResetsToHandleLease() throws InterruptedException {
        Lock lock = LockKit.tryLock(NAME, Duration.ofMillis(800));
        assertNotNull(lock);
        Thread.sleep(300L);

        assertTrue(lock.renew());
        Duration ttl = cache.getTtl(LockNames.CACHE_NAME, NAME);
        assertNotNull(ttl);
        assertTrue("续期应回到约 800ms，实际 " + ttl, ttl.toMillis() > 300);
        assertTrue(lock.release());
    }

    /**
     * 锁已过期时续期返回 {@code false}，不重建条目。
     */
    @Test
    public void renewFailsAfterExpiry() throws InterruptedException {
        Lock lock = LockKit.tryLock(NAME, Duration.ofMillis(200));
        assertNotNull(lock);
        Thread.sleep(300L);

        assertFalse(lock.renew());
        assertNull(cache.getTtl(LockNames.CACHE_NAME, NAME));
    }

    /**
     * 构造单实例 Redis 配置。
     *
     * @return 配置对象
     */
    private static Prop prop() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("cache.type", "jedis");
        prop.getProperties().setProperty("cache.namespace", NAMESPACE);
        prop.getProperties().setProperty("cache.ttlMillis", "60000");
        prop.getProperties().setProperty("cache.jedis.uri", REDIS_URI);
        return prop;
    }
}
