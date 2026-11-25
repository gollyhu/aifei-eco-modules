/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

import cn.hg.aifei.i18n.internal.I18nConfig;
import cn.hg.aifei.i18n.internal.I18nEngine;
import cn.hg.aifei.i18n.internal.I18nHolder;
import cn.hg.aifei.i18n.internal.LocaleContext;
import cn.hg.aifei.i18n.internal.LocaleMeta;
import cn.hg.aifei.i18n.support.FakeMessageSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 整包导出与单点一致性单测（dir 数据源审查与降级）。
 */
public class BundleExportTest {

    @Before
    public void setUp() {
        Properties props = new Properties();
        props.setProperty(I18nConfig.KEY_DEFAULT_LOCALE, "en");
        I18nConfig config = I18nConfig.parse(props);
        I18nHolder.install(new I18nEngine(config, new ArrayList<cn.hg.aifei.i18n.MessageSource>()));
        LocaleContext.clear();
        LocaleMeta.setDirectionResolver(null);
    }

    @After
    public void tearDown() {
        LocaleMeta.setDirectionResolver(null);
        LocaleContext.clear();
        I18nHolder.clear();
    }

    private static LanguageBundle export(FakeMessageSource... sources) {
        I18nEngine engine = I18nHolder.engine();
        // 用来源重建引擎，保持同一 config
        I18nHolder.install(new I18nEngine(engine.config(), new ArrayList<MessageSource>(Arrays.asList(sources))));
        return I18nKit.bundle("zh-CN");
    }

    @Test
    public void fourFieldsPresent() {
        LanguageBundle bundle = export(new FakeMessageSource("properties").put("zh-CN", "login.title", "登录"));
        assertEquals("zh-CN", bundle.locale());
        assertEquals("ltr", bundle.dir());
        assertEquals(Locale.forLanguageTag("zh-CN").getDisplayName(Locale.forLanguageTag("zh-CN")), bundle.localeName());
        assertEquals("登录", bundle.messages().get("login.title"));
    }

    @Test
    public void mergesListableSources() {
        LanguageBundle bundle = export(
                new FakeMessageSource("a").put("zh-CN", "k1", "v1"),
                new FakeMessageSource("b").put("zh-CN", "k2", "v2"));
        assertEquals("v1", bundle.messages().get("k1"));
        assertEquals("v2", bundle.messages().get("k2"));
    }

    @Test
    public void firstSourceWinsForDuplicateKey() {
        LanguageBundle bundle = export(
                new FakeMessageSource("a").put("zh-CN", "k", "first"),
                new FakeMessageSource("b").put("zh-CN", "k", "second"));
        assertEquals("first", bundle.messages().get("k"));
    }

    @Test
    public void keyOrderStableAcrossExports() {
        FakeMessageSource source = new FakeMessageSource("properties")
                .put("zh-CN", "z.key", "z").put("zh-CN", "a.key", "a").put("zh-CN", "m.key", "m");
        LanguageBundle first = export(source);
        LanguageBundle second = export(source);
        assertEquals(new ArrayList<>(first.messages().keySet()), new ArrayList<>(second.messages().keySet()));
        List<String> keys = new ArrayList<>(first.messages().keySet());
        List<String> sorted = new ArrayList<>(keys);
        java.util.Collections.sort(sorted);
        assertEquals("key 应按 String 自然序升序", sorted, keys);
    }

    @Test
    public void exportIgnoresContext() {
        LocaleContext.set("en");
        LanguageBundle bundle = export(new FakeMessageSource("properties").put("zh-CN", "k", "中文"));
        assertEquals("导出 locale 应为显式 zh-CN", "zh-CN", bundle.locale());
        assertEquals("中文", bundle.messages().get("k"));
    }

    @Test
    public void localizedValuesAreStringsAndRawPlaceholders() {
        LanguageBundle bundle = export(new FakeMessageSource("properties").put("zh-CN", "hello", "hello {0}"));
        assertEquals("hello {0}", bundle.messages().get("hello"));
        for (Map.Entry<String, String> entry : bundle.messages().entrySet()) {
            assertTrue("value 恒为 String", entry.getValue() instanceof String);
        }
    }

    @Test
    public void nonListableSourceSkippedButSingleHitWorks() {
        FakeMessageSource nonListable = new FakeMessageSource("remote").listable(false).put("zh-CN", "only", "remote-value");
        export(nonListable);
        // 单点取文案仍可命中不可列举来源
        assertEquals("remote-value", I18nKit.local("zh-CN").message("only"));
        // 整包不含该 key
        assertFalse(I18nKit.bundle("zh-CN").messages().containsKey("only"));
    }

    @Test
    public void listFailureSkippedExportSucceeds() {
        LanguageBundle bundle = export(new FakeMessageSource("db").listFailing(true));
        assertNotNull("列举故障不应阻断导出", bundle);
        assertTrue(bundle.messages().isEmpty());
    }

    @Test
    public void singleAndBundleConsistent() {
        FakeMessageSource source = new FakeMessageSource("properties")
                .put("zh-CN", "k1", "v1").put("zh-CN", "k2", "v2");
        export(source);
        LanguageBundle bundle = I18nKit.bundle("zh-CN");
        for (Map.Entry<String, String> entry : bundle.messages().entrySet()) {
            assertEquals("单点与整包取值应一致: " + entry.getKey(),
                    entry.getValue(), I18nKit.local("zh-CN").message(entry.getKey()));
        }
    }

    @Test
    public void dirRtlFromJdk() {
        I18nHolder.install(new I18nEngine(I18nHolder.engine().config(), new ArrayList<MessageSource>()));
        LanguageBundle ar = I18nKit.bundle("ar");
        assertEquals("rtl", ar.dir());
        LanguageBundle zh = I18nKit.bundle("zh-CN");
        assertEquals("ltr", zh.dir());
    }

    @Test
    public void dirDegradesToLtrWhenCapabilityUnavailable() {
        LocaleMeta.setDirectionResolver(locale -> {
            throw new LinkageError("模拟 java.desktop 缺失");
        });
        LanguageBundle bundle = export(new FakeMessageSource("properties").put("zh-CN", "k", "v"));
        assertEquals("降级为 ltr", "ltr", bundle.dir());
        assertEquals("导出仍成功", "v", bundle.messages().get("k"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalLocaleThrows() {
        export(new FakeMessageSource("properties"));
        I18nKit.bundle("zh_CN_");
    }

    @Test
    public void normalizedLocaleExportConsistent() {
        FakeMessageSource source = new FakeMessageSource("properties").put("zh-CN", "k", "v");
        export(source);
        LanguageBundle dashed = I18nKit.bundle("zh-CN");
        LanguageBundle underscored = I18nKit.bundle("zh_CN");
        assertEquals("zh-CN", underscored.locale());
        assertEquals(dashed.messages(), underscored.messages());
        assertEquals(new ArrayList<>(dashed.messages().keySet()), new ArrayList<>(underscored.messages().keySet()));
    }
}
