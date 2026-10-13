/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.dao;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/** 持久层（纯缓存形态）：秒级超时约定换算、对象与会话委托、搜索分页 */
public class SaTokenDaoForCacheTest {

    private final InMemoryCacheStub cache = new InMemoryCacheStub();
    private final SaTokenDao dao = new SaTokenDaoForCache(cache);

    // ─────────── 字符串读写与超时约定 ───────────

    @Test
    public void storesAndReadsStringWithTimeout() {
        dao.set("k1", "v1", 100);

        Assert.assertEquals("v1", dao.get("k1"));
        Assert.assertEquals(Duration.ofSeconds(100), cache.getTtl("satoken", "k1"));
    }

    @Test
    public void neverExpireTimeoutMapsToCacheMaximumTtl() {
        dao.set("k1", "v1", SaTokenDao.NEVER_EXPIRE);

        Assert.assertEquals(Duration.ofSeconds(Integer.MAX_VALUE), cache.getTtl("satoken", "k1"));
        Assert.assertEquals(SaTokenDao.NEVER_EXPIRE, dao.getTimeout("k1"));
    }

    @Test
    public void nonPositiveTimeoutSkipsWrite() {
        dao.set("zero", "v", 0);
        dao.set("negative", "v", SaTokenDao.NOT_VALUE_EXPIRE);

        Assert.assertFalse("timeout=0 按约定不存储", cache.exists("satoken", "zero"));
        Assert.assertFalse("timeout<=-2 按约定不存储", cache.exists("satoken", "negative"));
    }

    @Test
    public void deleteRemovesEntry() {
        dao.set("k1", "v1", 100);
        dao.delete("k1");

        Assert.assertNull(dao.get("k1"));
    }

    @Test
    public void getTimeoutReportsAbsentEntry() {
        Assert.assertEquals(SaTokenDao.NOT_VALUE_EXPIRE, dao.getTimeout("missing"));
    }

    @Test
    public void updateTimeoutResetsTtlWithoutTouchingValue() {
        dao.set("k1", "v1", 100);
        dao.updateTimeout("k1", 500);

        Assert.assertEquals("v1", dao.get("k1"));
        Assert.assertEquals(Duration.ofSeconds(500), cache.getTtl("satoken", "k1"));
    }

    @Test
    public void updateTimeoutWithNeverExpireMapsToMaximumTtl() {
        dao.set("k1", "v1", 100);
        dao.updateTimeout("k1", SaTokenDao.NEVER_EXPIRE);

        Assert.assertEquals(SaTokenDao.NEVER_EXPIRE, dao.getTimeout("k1"));
    }

    // ─────────── 对象读写与会话 ───────────

    @Test
    public void storesAndReadsObject() {
        dao.setObject("o1", "payload", 100);

        Assert.assertEquals("payload", dao.getObject("o1"));
        Assert.assertEquals("payload", dao.getObject("o1", String.class));
    }

    @Test(expected = ClassCastException.class)
    public void getObjectWithWrongTypeFails() {
        dao.setObject("o1", "payload", 100);
        dao.getObject("o1", Integer.class);
    }

    @Test
    public void sessionRoundTrip() {
        SaSession session = new SaSession("session-1");
        session.set("userId", 42);

        dao.setSession(session, 100);
        SaSession loaded = dao.getSession("session-1");

        Assert.assertNotNull(loaded);
        Assert.assertEquals("session-1", loaded.getId());
        Assert.assertEquals(42, loaded.get("userId"));

        dao.deleteSession("session-1");
        Assert.assertNull(dao.getSession("session-1"));
    }

    @Test
    public void sessionTimeoutDelegatesToObjectTimeout() {
        dao.setSession(new SaSession("session-1"), 100);

        Assert.assertEquals(100, dao.getSessionTimeout("session-1"));

        dao.updateSessionTimeout("session-1", 300);
        Assert.assertEquals(300, dao.getSessionTimeout("session-1"));
    }

    // ─────────── 更新语义 ───────────

    @Test
    public void updateKeepsRemainingTtl() {
        dao.set("k1", "before", 200);
        dao.update("k1", "after");

        Assert.assertEquals("after", dao.get("k1"));
        Assert.assertEquals(Duration.ofSeconds(200), cache.getTtl("satoken", "k1"));
    }

    @Test
    public void updateSkipsAbsentEntry() {
        dao.update("missing", "value");

        Assert.assertFalse("不存在时不应重建", cache.exists("satoken", "missing"));
    }

    // ─────────── 搜索 ───────────

    @Test
    public void searchDataFiltersByPrefixAndKeyword() {
        seedKeys();

        List<String> byPrefix = dao.searchData("satoken:login:token:", null, 0, -1, true);
        Assert.assertEquals(2, byPrefix.size());
        Assert.assertEquals("satoken:login:token:a", byPrefix.get(0));
        Assert.assertEquals("satoken:login:token:b", byPrefix.get(1));

        List<String> byKeyword = dao.searchData("satoken:login:", "session", 0, -1, true);
        Assert.assertEquals(Collections.singletonList("satoken:login:session:1"), byKeyword);
    }

    @Test
    public void searchDataReversesWhenSortTypeIsFalse() {
        seedKeys();

        List<String> desc = dao.searchData("satoken:login:token:", null, 0, -1, false);

        Assert.assertEquals(2, desc.size());
        Assert.assertEquals("satoken:login:token:b", desc.get(0));
        Assert.assertEquals("satoken:login:token:a", desc.get(1));
    }

    @Test
    public void searchDataPaginatesAndToleratesOutOfRangeStart() {
        seedKeys();

        // 字典序：session:1 < token:a < token:b，故跳 1 条后取到 token:a
        List<String> page = dao.searchData("", null, 1, 1, true);
        Assert.assertEquals(1, page.size());
        Assert.assertEquals("satoken:login:token:a", page.get(0));

        Assert.assertTrue(dao.searchData("", null, 0, 2, true).size() == 2);
        Assert.assertTrue("start 越界返回空", dao.searchData("", null, 99, 5, true).isEmpty());
        Assert.assertTrue("负 start 按 0 处理", !dao.searchData("", null, -5, 1, true).isEmpty());
    }

    @Test
    public void searchDataWithNoMatchReturnsEmpty() {
        seedKeys();

        Assert.assertTrue(dao.searchData("nope:", null, 0, -1, true).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullCache() {
        new SaTokenDaoForCache(null);
    }

    /** 三条键，覆盖前缀命中、关键字命中与字典序 */
    private void seedKeys() {
        dao.set("satoken:login:token:b", "1", 100);
        dao.set("satoken:login:token:a", "1", 100);
        dao.set("satoken:login:session:1", "1", 100);
    }
}
