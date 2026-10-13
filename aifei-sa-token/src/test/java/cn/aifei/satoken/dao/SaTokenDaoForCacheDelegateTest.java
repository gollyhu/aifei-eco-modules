/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.dao;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.session.SaSession;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

/**
 * 持久层（缓存加速 + 权威委托形态）：回源回填、写穿、超时以委托层为准、搜索委托。
 *
 * <p>委托层用 Sa-Token 内置的内存实现 {@link SaTokenDaoDefaultImpl}，使两侧语义都是真实的
 * Sa-Token 契约，而非测试自造的近似。</p>
 */
public class SaTokenDaoForCacheDelegateTest {

    /** 超时断言容差：委托层按秒计算剩余时间，跨秒调用可能相差 1 秒 */
    private static final long TOLERANCE = 1L;

    private final InMemoryCacheStub cache = new InMemoryCacheStub();
    private final SaTokenDao delegate = new SaTokenDaoDefaultImpl();
    private final SaTokenDao dao = new SaTokenDaoForCache(cache, delegate);

    // ─────────── 读：回源并回填 ───────────

    @Test
    public void readsThroughToDelegateAndBackfillsCache() {
        delegate.set("k1", "from-delegate", 200);

        Assert.assertEquals("from-delegate", dao.get("k1"));
        Assert.assertEquals("回源结果应回填缓存", "from-delegate", cache.get("satoken", "k1"));
    }

    @Test
    public void backfilledTtlComesFromDelegate() {
        delegate.set("k1", "v", 200);
        dao.get("k1");

        Assert.assertEquals("回填 TTL 应以委托层的剩余超时为准",
                delegate.getTimeout("k1"), cache.getTtl("satoken", "k1").getSeconds(), TOLERANCE);
    }

    @Test
    public void servesFromCacheWithoutHittingDelegate() {
        cache.put("satoken", "k1", "from-cache", Duration.ofSeconds(100));

        Assert.assertEquals("from-cache", dao.get("k1"));
        Assert.assertNull("缓存命中时不应回源", delegate.get("k1"));
    }

    @Test
    public void missesReturnNullWhenBothLayersEmpty() {
        Assert.assertNull(dao.get("absent"));
    }

    // ─────────── 写：先落委托层再刷新缓存 ───────────

    @Test
    public void writesThroughToBothLayers() {
        dao.set("k1", "v1", 100);

        Assert.assertEquals("v1", delegate.get("k1"));
        Assert.assertEquals("v1", cache.get("satoken", "k1"));
    }

    @Test
    public void updatePropagatesToBothLayers() {
        dao.set("k1", "before", 200);
        dao.update("k1", "after");

        Assert.assertEquals("after", delegate.get("k1"));
        Assert.assertEquals("after", cache.get("satoken", "k1"));
    }

    @Test
    public void deletesFromBothLayers() {
        dao.set("k1", "v1", 100);
        dao.delete("k1");

        Assert.assertNull(delegate.get("k1"));
        Assert.assertFalse(cache.exists("satoken", "k1"));
    }

    // ─────────── 超时：以委托层为准 ───────────

    @Test
    public void timeoutComesFromDelegate() {
        dao.set("k1", "v", 300);

        Assert.assertEquals(300, dao.getTimeout("k1"), TOLERANCE);
    }

    @Test
    public void updateTimeoutAppliesToBothLayers() {
        dao.set("k1", "v", 100);
        dao.updateTimeout("k1", 400);

        Assert.assertEquals(400, delegate.getTimeout("k1"), TOLERANCE);
        Assert.assertEquals(400, cache.getTtl("satoken", "k1").getSeconds());
    }

    // ─────────── 搜索：以委托层为准 ───────────

    @Test
    public void searchDataComesFromDelegate() {
        delegate.set("satoken:login:token:b", "1", 100);
        delegate.set("satoken:login:token:a", "1", 100);

        List<String> result = dao.searchData("satoken:login:token:", null, 0, -1, true);

        Assert.assertEquals("搜索结果应完整来自委托层，不受缓存覆盖范围影响", 2, result.size());
    }

    // ─────────── 对象与会话 ───────────

    @Test
    public void objectReadsThroughAndBackfills() {
        delegate.setObject("o1", "payload", 200);

        Assert.assertEquals("payload", dao.getObject("o1"));
        Assert.assertEquals("payload", dao.getObject("o1", String.class));
        Assert.assertEquals("payload", cache.get("satoken", "o1"));
    }

    @Test
    public void objectWritesThroughToBothLayers() {
        dao.setObject("o1", "payload", 100);

        Assert.assertEquals("payload", delegate.getObject("o1"));
        Assert.assertEquals("payload", cache.get("satoken", "o1"));
    }

    @Test
    public void sessionReadsThroughAndBackfills() {
        delegate.setSession(new SaSession("session-1"), 200);

        SaSession loaded = dao.getSession("session-1");

        Assert.assertNotNull(loaded);
        Assert.assertEquals("session-1", loaded.getId());
        Assert.assertNotNull("会话应回填缓存", cache.get("satoken", "session-1"));
    }

    @Test
    public void sessionWritesAndDeletesThroughBothLayers() {
        dao.setSession(new SaSession("session-1"), 100);

        Assert.assertNotNull(delegate.getSession("session-1"));
        Assert.assertNotNull(cache.get("satoken", "session-1"));

        dao.deleteSession("session-1");

        Assert.assertNull(delegate.getSession("session-1"));
        Assert.assertFalse(cache.exists("satoken", "session-1"));
    }

    @Test
    public void sessionUpdateKeepsAuthorityValue() {
        dao.setSession(new SaSession("session-1"), 200);
        SaSession updated = new SaSession("session-1");
        updated.set("userId", 7);

        dao.updateSession(updated);

        Assert.assertEquals(7, delegate.getSession("session-1").get("userId"));
        Assert.assertEquals(7, ((SaSession) cache.get("satoken", "session-1")).get("userId"));
    }
}
