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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 格式化薄门面单测（AC-16 / D-03）。
 *
 * <p>断言严格遵守跨 provider 稳定口径：不写死货币符号字符与完整日期串，只断言「不相等 / 含本币种符号 /
 * 月份名与日的相对位置 / 小数位」等稳定性质（设计 §9.8）。</p>
 */
public class FormatKitTest {

    private static final Date INSTANT = new GregorianCalendar(2026, Calendar.NOVEMBER, 18, 12, 0, 0).getTime();

    @Before
    public void setUp() {
        Properties props = new Properties();
        props.setProperty(I18nConfig.KEY_DEFAULT_LOCALE, "en-US");
        I18nConfig config = I18nConfig.parse(props);
        I18nHolder.install(new I18nEngine(config, new ArrayList<cn.hg.aifei.i18n.MessageSource>()));
        LocaleContext.clear();
    }

    @After
    public void tearDown() {
        I18nHolder.clear();
        LocaleContext.clear();
    }

    @Test
    public void numberSeparatorsFollowLocale() {
        String de = FormatKit.formatNumber(1234.5, "de-DE");
        String us = FormatKit.formatNumber(1234.5, "en-US");
        assertFalse("两个 locale 的写法应不同：" + de + " / " + us, de.equals(us));
        assertTrue("de-DE 含逗号小数/点千位：" + de, de.contains("."));
        assertTrue("en-US 含逗号千位：" + us, us.contains(","));
    }

    @Test
    public void currencyIsOrthogonalToLocale() {
        String usd = FormatKit.formatCurrency(1234.5, "USD", "zh-CN");
        String cny = FormatKit.formatCurrency(1234.5, "CNY", "zh-CN");
        assertFalse("USD 与 CNY 输出应不同", usd.equals(cny));
        assertTrue("USD 输出含本币种符号：" + usd, usd.contains(Currency.getInstance("USD").getSymbol(Locale.forLanguageTag("zh-CN"))));
        assertTrue("CNY 输出含本币种符号：" + cny, cny.contains(Currency.getInstance("CNY").getSymbol(Locale.forLanguageTag("zh-CN"))));
    }

    @Test
    public void currencyFractionDigitsDrivenByCurrency() {
        String jpy = FormatKit.formatCurrency(1234.5, "JPY", "en-US");
        String usd = FormatKit.formatCurrency(1234.5, "USD", "en-US");
        assertFalse("JPY 应为 0 位小数、USD 为 2 位：" + jpy + " / " + usd, jpy.equals(usd));
        assertTrue("JPY 输出不含小数分隔符：" + jpy, !jpy.contains("."));
    }

    @Test
    public void sameCurrencyDifferentLocaleOutputDiffers() {
        String zh = FormatKit.formatCurrency(1234.5, "USD", "zh-CN");
        String us = FormatKit.formatCurrency(1234.5, "USD", "en-US");
        assertFalse("同币种不同 locale 写法应不同：" + zh + " / " + us, zh.equals(us));
    }

    @Test
    public void dateFieldOrderFollowsLocale() {
        String us = FormatKit.formatDate(INSTANT, DateStyle.MEDIUM, "en-US");
        String gb = FormatKit.formatDate(INSTANT, DateStyle.MEDIUM, "en-GB");
        String month = new java.text.SimpleDateFormat("MMM", Locale.ENGLISH).format(INSTANT);
        int usMonth = us.indexOf(month);
        int gbMonth = gb.indexOf(month);
        assertTrue("应能定位月份锚点：" + us, usMonth >= 0);
        assertTrue("应能定位月份锚点：" + gb, gbMonth >= 0);
        assertTrue("en-US 月份在日前、en-GB 月份在日后：" + us + " / " + gb,
                us.indexOf("18") > usMonth && gb.indexOf("18") < gbMonth);
    }

    @Test
    public void timeZoneIsOrthogonalToLocale() {
        String shanghai = FormatKit.formatDateTime(INSTANT, TimeZone.getTimeZone("Asia/Shanghai"), DateStyle.MEDIUM, "en-US");
        String berlin = FormatKit.formatDateTime(INSTANT, TimeZone.getTimeZone("Europe/Berlin"), DateStyle.MEDIUM, "en-US");
        assertFalse("同刻不同区输出时刻应不同：" + shanghai + " / " + berlin, shanghai.equals(berlin));
    }

    @Test
    public void percentFollowsLocale() {
        String de = FormatKit.formatPercent(0.85, "de-DE");
        String us = FormatKit.formatPercent(0.85, "en-US");
        assertTrue("百分比应含 85：" + de, de.contains("85"));
        assertTrue("百分比应含 85：" + us, us.contains("85"));
        assertTrue("百分比应含量词 %：" + de, de.contains("%"));
        assertTrue("百分比应含量词 %：" + us, us.contains("%"));
    }

    @Test
    public void defaultStyleIsMedium() {
        String implicit = FormatKit.formatDate(INSTANT, "en-US");
        String explicitMedium = FormatKit.formatDate(INSTANT, DateStyle.MEDIUM, "en-US");
        assertEquals("省略档位应与显式 MEDIUM 逐字一致", explicitMedium, implicit);
        String implicitDateTime = FormatKit.formatDateTime(INSTANT, TimeZone.getTimeZone("Asia/Shanghai"), "en-US");
        String explicitDateTimeMedium = FormatKit.formatDateTime(INSTANT, TimeZone.getTimeZone("Asia/Shanghai"), DateStyle.MEDIUM, "en-US");
        assertEquals("日期时间省略档位应与 MEDIUM 逐字一致", explicitDateTimeMedium, implicitDateTime);
    }

    @Test
    public void fourStylesAreAvailable() {
        assertNotNull(FormatKit.formatDate(INSTANT, DateStyle.SHORT, "en-US"));
        assertNotNull(FormatKit.formatDate(INSTANT, DateStyle.MEDIUM, "en-US"));
        assertNotNull(FormatKit.formatDate(INSTANT, DateStyle.LONG, "en-US"));
        assertNotNull(FormatKit.formatDate(INSTANT, DateStyle.FULL, "en-US"));
        assertFalse("SHORT 与 FULL 输出应不同",
                FormatKit.formatDate(INSTANT, DateStyle.SHORT, "en-US")
                        .equals(FormatKit.formatDate(INSTANT, DateStyle.FULL, "en-US")));
    }

    @Test
    public void localeResolutionReusesContext() {
        LocaleContext.set("en-GB");
        String viaContext = FormatKit.formatNumber(1234.5);
        String explicit = FormatKit.formatNumber(1234.5, "en-GB");
        assertEquals("上下文 locale 应与显式同 locale 逐字一致", explicit, viaContext);
    }

    @Test
    public void localeFallsBackToDefaultWhenNoContext() {
        String viaDefault = FormatKit.formatNumber(1234.5);
        String explicit = FormatKit.formatNumber(1234.5, "en-US");
        assertEquals("无上下文应落 defaultLocale", explicit, viaDefault);
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalCurrencyThrows() {
        FormatKit.formatCurrency(10, "XYZ", "en-US");
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalZoneThrows() {
        // 用自定义 ID 构造非法时区：TimeZone.getTimeZone("Mars/Base") 会静默返回 GMT，故用 SimpleTimeZone 保留非法 ID
        FormatKit.formatDateTime(INSTANT, new SimpleTimeZone(0, "Mars/Base"), DateStyle.MEDIUM, "en-US");
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullCurrencyThrows() {
        FormatKit.formatCurrency(10, (String) null, "en-US");
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullZoneThrows() {
        FormatKit.formatDateTime(INSTANT, null, DateStyle.MEDIUM, "en-US");
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullDateThrows() {
        FormatKit.formatDate(null, DateStyle.MEDIUM, "en-US");
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullStyleThrows() {
        FormatKit.formatDate(INSTANT, (DateStyle) null, "en-US");
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalExplicitLocaleThrows() {
        FormatKit.formatNumber(1234.5, "zh_CN_");
    }
}
