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
import cn.hg.aifei.i18n.internal.LocaleNormalizer;
import cn.hg.aifei.i18n.internal.LocaleResolver;
import java.util.ArrayList;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * locale 解析单测（AC-08 / AC-14-3 / §5.5 四级优先级与规范化唯一定义点）。
 */
public class LocaleResolverTest {

    @Before
    public void setUp() {
        LocaleContext.clear();
    }

    @After
    public void tearDown() {
        I18nHolder.clear();
        LocaleContext.clear();
    }

    private static void install(String defaultLocale, String locales) {
        Properties props = new Properties();
        props.setProperty(I18nConfig.KEY_DEFAULT_LOCALE, defaultLocale);
        if (locales != null) {
            props.setProperty(I18nConfig.KEY_LOCALES, locales);
        }
        I18nConfig config = I18nConfig.parse(props);
        I18nHolder.install(new I18nEngine(config, new ArrayList<cn.hg.aifei.i18n.MessageSource>()));
    }

    @Test
    public void explicitWinsOverContext() {
        install("en", null);
        LocaleContext.set("en");
        assertEquals("zh-CN", LocaleResolver.resolve("zh-CN"));
    }

    @Test
    public void contextUsedWhenNoExplicit() {
        install("en", null);
        LocaleContext.set("fr-FR");
        assertEquals("fr-FR", LocaleResolver.resolve(null));
    }

    @Test
    public void fallbackToDefaultWhenAllAbsent() {
        install("en", null);
        assertEquals("en", LocaleResolver.resolve(null));
    }

    @Test
    public void explicitNormalized() {
        install("en", null);
        assertEquals("zh-CN", LocaleResolver.resolve("zh_CN"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void explicitIllegalThrows() {
        install("en", null);
        LocaleResolver.resolve("zh_CN_");
    }

    @Test
    public void acceptLanguageFirstSupported() {
        install("en", "zh-CN,en");
        assertEquals("zh-CN", LocaleResolver.parseAcceptLanguage("zh-CN,en;q=0.8"));
    }

    @Test
    public void acceptLanguageSkipsIllegalSegment() {
        install("en", "en,zh-CN");
        assertEquals("en", LocaleResolver.parseAcceptLanguage("zh_CN_,en;q=0.8"));
    }

    @Test
    public void acceptLanguageWhitelistFilter() {
        install("en", "zh-CN,en");
        assertEquals("跳过白名单外的 fr-FR，命中首个受支持的 zh-CN",
                "zh-CN", LocaleResolver.parseAcceptLanguage("fr-FR,zh-CN;q=0.8"));
        assertNull("全部片段都不在白名单时返回 null", LocaleResolver.parseAcceptLanguage("fr-FR,de-DE"));
    }

    @Test
    public void acceptLanguageNoWhitelistAcceptsAnyLegal() {
        install("en", null);
        assertEquals("fr-FR", LocaleResolver.parseAcceptLanguage("fr-FR"));
    }

    @Test
    public void acceptLanguageAllIllegalReturnsNull() {
        install("en", "en");
        assertNull(LocaleResolver.parseAcceptLanguage("zh_CN_,bad_tag_"));
    }

    @Test
    public void acceptLanguageNullReturnsNull() {
        install("en", null);
        assertNull(LocaleResolver.parseAcceptLanguage(null));
    }

    @Test
    public void normalizerZhHantTw() {
        assertEquals("zh-Hant-TW", LocaleNormalizer.normalize("zh-hant-tw"));
    }

    @Test
    public void tryNormalizeIllegalReturnsNull() {
        assertNull(LocaleNormalizer.tryNormalize("zh_CN_"));
        assertEquals("zh-CN", LocaleNormalizer.tryNormalize("zh_CN"));
    }
}
