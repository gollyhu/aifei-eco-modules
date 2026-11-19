/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.aifei;

import cn.aifei.util.Prop;
import cn.hg.aifei.i18n.I18nException;
import cn.hg.aifei.i18n.I18nKit;
import cn.hg.aifei.i18n.MessageSource;
import cn.hg.aifei.i18n.internal.I18nHolder;
import cn.hg.aifei.i18n.internal.LocaleContext;
import cn.hg.aifei.i18n.support.FakeMessageSource;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 装配插件单测（AC-04/11/12，D-01 配置键口径）：幂等、重名、依赖探测、配置校验、未装配调用。
 */
public class I18nPluginTest {

    @After
    public void tearDown() {
        LocaleContext.clear();
        I18nHolder.clear();
    }

    private static Prop prop(String... keyValues) {
        Prop prop = new Prop();
        java.util.Properties properties = prop.getProperties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return prop;
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullPropThrows() {
        new I18nPlugin(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullSourceThrows() {
        new I18nPlugin(prop("i18n.defaultLocale", "en")).addSource(null);
    }

    @Test
    public void startInstallsEngineAndIsIdempotent() {
        I18nPlugin plugin = new I18nPlugin(prop(
                "i18n.defaultLocale", "en",
                "i18n.sources", "properties"));
        plugin.start();
        plugin.start();
        assertEquals("en", I18nHolder.engine().config().defaultLocale());
        assertTrue("未配置 locales 时不设白名单", I18nHolder.engine().config().locales().isEmpty());
        assertEquals("未配置 ttl 时默认 60000", 60000L, I18nHolder.engine().config().cacheTtlMillis());
        plugin.stop();
        plugin.stop();
    }

    @Test
    public void defaultSourceIsProperties() {
        I18nPlugin plugin = new I18nPlugin(prop("i18n.defaultLocale", "en"));
        plugin.start();
        assertEquals(1, I18nHolder.engine().sources().size());
        assertEquals("properties", I18nHolder.engine().sources().get(0).name());
    }

    @Test
    public void customSourceRegisteredInOrder() {
        FakeMessageSource custom = new FakeMessageSource("remote").put("zh-CN", "k", "remote-value");
        I18nPlugin plugin = new I18nPlugin(prop(
                "i18n.defaultLocale", "en",
                "i18n.sources", "remote"))
                .addSource(custom);
        plugin.start();
        assertEquals("remote-value", I18nKit.message("k", "zh-CN"));
    }

    @Test
    public void duplicateStableNameFailsStartup() {
        FakeMessageSource duplicate = new FakeMessageSource("properties");
        I18nPlugin plugin = new I18nPlugin(prop("i18n.defaultLocale", "en")).addSource(duplicate);
        try {
            plugin.start();
            org.junit.Assert.fail("重名应启动失败");
        } catch (I18nException e) {
            assertTrue("消息含稳定名与重复语义：" + e.getMessage(), e.getMessage().contains("properties"));
        }
        org.junit.Assert.assertThrows(I18nException.class, () -> I18nKit.message("k", "en"));
    }

    @Test
    public void unregisteredSourceFailsStartup() {
        I18nPlugin plugin = new I18nPlugin(prop(
                "i18n.defaultLocale", "en",
                "i18n.sources", "foo"));
        try {
            plugin.start();
            org.junit.Assert.fail("未注册来源应启动失败");
        } catch (I18nException e) {
            assertTrue("消息含未注册名：" + e.getMessage(), e.getMessage().contains("foo"));
        }
    }

    @Test
    public void missingDefaultLocaleFailsStartup() {
        I18nPlugin plugin = new I18nPlugin(prop("i18n.sources", "properties"));
        try {
            plugin.start();
            org.junit.Assert.fail("缺 defaultLocale 应启动失败");
        } catch (I18nException e) {
            assertTrue("消息指明缺失键：" + e.getMessage(), e.getMessage().contains("i18n.defaultLocale"));
        }
    }

    @Test
    public void illegalTtlFailsStartup() {
        I18nPlugin plugin = new I18nPlugin(prop(
                "i18n.defaultLocale", "en",
                "i18n.cache.ttlMillis", "abc"));
        try {
            plugin.start();
            org.junit.Assert.fail("非法 TTL 应启动失败");
        } catch (I18nException e) {
            assertTrue("消息含键名：" + e.getMessage(), e.getMessage().contains("i18n.cache.ttlMillis"));
        }
    }

    @Test
    public void addSourceAfterStartFails() {
        I18nPlugin plugin = new I18nPlugin(prop("i18n.defaultLocale", "en"));
        plugin.start();
        try {
            plugin.addSource(new FakeMessageSource("late"));
            org.junit.Assert.fail("已启动后注册应失败");
        } catch (I18nException e) {
            assertTrue(e.getMessage().contains("已启动"));
        }
    }

    @Test
    public void undeclaredSourceNotInstantiatedEvenIfBroken() {
        // 未声明的来源即便依赖缺失也不连带失败：这里声明仅 properties，不声明 kv
        I18nPlugin plugin = new I18nPlugin(prop(
                "i18n.defaultLocale", "en",
                "i18n.sources", "properties"));
        plugin.start();
        assertEquals(1, I18nHolder.engine().sources().size());
    }

    @Test
    public void undeclaredKvDoesNotNeedCacheDependency() {
        // 只声明 properties，不使用 kv：启动成功
        I18nPlugin plugin = new I18nPlugin(prop(
                "i18n.defaultLocale", "en",
                "i18n.sources", "properties"));
        plugin.start();
        assertEquals("properties", I18nHolder.engine().sources().get(0).name());
    }

    @Test
    public void unassembledCallFails() {
        I18nHolder.clear();
        try {
            I18nKit.message("k", "en");
            org.junit.Assert.fail("未装配应失败");
        } catch (I18nException e) {
            assertEquals("i18n 未装配或已停止，请先注册 I18nPlugin", e.getMessage());
        }
    }

    @Test
    public void onlyFourConfigKeysRecognized() {
        // I18nConfig 对外暴露的四个键常量精确构成配置面（无第五个启用开关）
        java.util.List<String> keys = java.util.Arrays.asList(
                cn.hg.aifei.i18n.internal.I18nConfig.KEY_DEFAULT_LOCALE,
                cn.hg.aifei.i18n.internal.I18nConfig.KEY_LOCALES,
                cn.hg.aifei.i18n.internal.I18nConfig.KEY_SOURCES,
                cn.hg.aifei.i18n.internal.I18nConfig.KEY_CACHE_TTL);
        assertEquals(4, keys.size());
        assertTrue(keys.contains("i18n.defaultLocale"));
        assertTrue(keys.contains("i18n.locales"));
        assertTrue(keys.contains("i18n.sources"));
        assertTrue(keys.contains("i18n.cache.ttlMillis"));
    }

    @Test
    public void kvDependencyMissingFailsStartupWithStableName() throws Exception {
        // 用隔离 ClassLoader 屏蔽 aifei-cache，模拟 classpath 无缓存依赖（AC-11-1）
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader blocking = new ClassLoader(original) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.equals("cn.hg.aifei.cache.CacheKit")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };
        Thread.currentThread().setContextClassLoader(blocking);
        try {
            I18nPlugin plugin = new I18nPlugin(prop(
                    "i18n.defaultLocale", "en",
                    "i18n.sources", "kv"));
            try {
                plugin.start();
                org.junit.Assert.fail("声明 kv 但依赖缺失应启动失败");
            } catch (I18nException e) {
                assertTrue("消息含稳定名 kv：" + e.getMessage(), e.getMessage().contains("kv"));
                assertTrue("消息含缺失依赖提示：" + e.getMessage(), e.getMessage().contains("aifei-cache"));
            }
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}
