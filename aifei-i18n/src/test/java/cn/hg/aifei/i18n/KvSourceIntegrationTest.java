/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

import cn.aifei.log.LogFactory;
import cn.aifei.log.LogKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.cache.CacheKit;
import cn.hg.aifei.cache.CachePlugin;
import cn.hg.aifei.i18n.aifei.I18nPlugin;
import cn.hg.aifei.i18n.support.RecordingLogFactory;
import java.net.URI;
import java.util.UUID;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.RedisClient;

import static org.junit.Assert.assertEquals;

/**
 * kv 来源真实集成测试，默认关闭；用 {@code -Dkv.integration=true -Dredis.uri=redis://127.0.0.1:6379/0} 开启。
 *
 * <p>连接地址仅允许回环（零联网纪律）。开启后先 ping 断言 PONG，连不上即失败、不静默跳过。
 * 断言真实写入 + 单条取命中 + 不可列举跳过 + refresh 后缓存失效；缓存键格式与 {@code KvSource}
 * 一致（locale + U+0001 + key），仅测试可见。</p>
 */
public class KvSourceIntegrationTest {

    private static final boolean ENABLED = Boolean.getBoolean("kv.integration");

    private static final String REDIS_URI = System.getProperty("redis.uri", "redis://127.0.0.1:6379/0");

    private static final String CACHE_NAME = "aifei_i18n_message";

    private static final String CACHE_KEY = "zh-CN\u0001it.title";

    private static final String NAMESPACE = "aifei_i18n_it_" + UUID.randomUUID().toString().replace("-", "");

    private static RedisClient raw;

    private static CachePlugin cachePlugin;

    private static I18nPlugin i18nPlugin;

    private static LogFactory originalLogFactory;

    @BeforeClass
    public static void setUpClass() {
        Assume.assumeTrue("kv integration tests: enable -Dkv.integration=true", ENABLED);
        originalLogFactory = LogKit.get().getLogFactory();
        LogKit.get().setLogFactory(new RecordingLogFactory());
        raw = RedisClient.create(URI.create(REDIS_URI));
        assertEquals("PONG", raw.ping());

        Prop cacheProp = new Prop();
        cacheProp.getProperties().setProperty("cache.type", "jedis");
        cacheProp.getProperties().setProperty("cache.namespace", NAMESPACE);
        cacheProp.getProperties().setProperty("cache.jedis.uri", REDIS_URI);
        cachePlugin = new CachePlugin(cacheProp);
        cachePlugin.start();

        Prop i18nProp = new Prop();
        i18nProp.getProperties().setProperty("i18n.defaultLocale", "en-US");
        i18nProp.getProperties().setProperty("i18n.sources", "kv");
        i18nPlugin = new I18nPlugin(i18nProp);
        i18nPlugin.start();
    }

    @AfterClass
    public static void tearDownClass() {
        if (i18nPlugin != null) {
            i18nPlugin.stop();
        }
        if (cachePlugin != null) {
            try {
                CacheKit.clear(CACHE_NAME);
            } finally {
                cachePlugin.stop();
            }
        }
        if (raw != null) {
            raw.close();
        }
        if (originalLogFactory != null) {
            LogKit.get().setLogFactory(originalLogFactory);
        }
    }

    @Before
    public void reset() {
        I18nKit.refresh();
        CacheKit.put(CACHE_NAME, CACHE_KEY, "标题-zh");
    }

    @Test
    public void singleHitAndBundleSkipsNonListableSource() {
        assertEquals("标题-zh", I18nKit.local("zh-CN").message("it.title"));
        LanguageBundle bundle = I18nKit.bundle("zh-CN");
        assertEquals("kv 不可列举，整包不应含该来源 key", 0, bundle.messages().size());
    }

    @Test
    public void refreshInvalidatesCache() {
        assertEquals("标题-zh", I18nKit.local("zh-CN").message("it.title"));
        CacheKit.put(CACHE_NAME, CACHE_KEY, "标题-zh-v2");
        assertEquals("TTL 内仍读缓存", "标题-zh", I18nKit.local("zh-CN").message("it.title"));
        I18nKit.refresh();
        assertEquals("refresh 后重新回源", "标题-zh-v2", I18nKit.local("zh-CN").message("it.title"));
    }
}
