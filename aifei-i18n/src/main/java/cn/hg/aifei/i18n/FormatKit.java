/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

import cn.hg.aifei.i18n.internal.FormatSupport;
import java.util.Date;
import java.util.TimeZone;

/**
 * Locale 贯通的格式化薄门面：数值（含百分比）/ 货币 / 日期 / 日期时间四项。
 *
 * <p>每个能力都提供不传 locale（走四级优先级）与显式 locale 两个重载；日期 / 日期时间
 * 在此基础上再提供省略档位（默认 MEDIUM）与显式档位两组。</p>
 *
 * <p>维度正交：币种是独立参数（不从 locale 反推）、时区是独立参数（与 locale 不互推）、
 * 档位仅日期类有。规则数据全部由 JDK 提供，本类零自建。</p>
 */
public final class FormatKit {

    private FormatKit() {
    }

    // ---- 数值（无档位维度）----

    /**
     * 数值格式化，locale 走四级优先级。
     *
     * @param value 待格式化的数值
     * @return 本地化字符串
     */
    public static String formatNumber(double value) {
        return formatNumber(value, null);
    }

    /**
     * 数值格式化。
     *
     * @param value     待格式化的数值
     * @param localeTag BCP 47 locale（可下划线写法）；显式非法 → IAE
     * @return 本地化字符串
     */
    public static String formatNumber(double value, String localeTag) {
        return FormatSupport.number(value, localeTag);
    }

    // ---- 百分比（无档位维度）----

    /**
     * 百分比格式化（{@code 0.85 → 85%}），locale 走四级优先级。
     *
     * @param ratio 比例值
     * @return 本地化字符串
     */
    public static String formatPercent(double ratio) {
        return formatPercent(ratio, null);
    }

    /**
     * 百分比格式化。
     *
     * @param ratio     比例值
     * @param localeTag BCP 47 locale；显式非法 → IAE
     * @return 本地化字符串
     */
    public static String formatPercent(double ratio, String localeTag) {
        return FormatSupport.percent(ratio, localeTag);
    }

    // ---- 货币（无档位维度；币种为独立参数）----

    /**
     * 货币格式化，locale 走四级优先级；小数位由币种决定。
     *
     * @param amount       金额
     * @param currencyCode 币种代码（如 {@code USD}），独立参数
     * @return 本地化字符串
     */
    public static String formatCurrency(double amount, String currencyCode) {
        return formatCurrency(amount, currencyCode, null);
    }

    /**
     * 货币格式化。
     *
     * @param amount       金额
     * @param currencyCode 币种代码，独立参数；{@code null} / 非法 → IAE
     * @param localeTag    BCP 47 locale；显式非法 → IAE
     * @return 本地化字符串
     */
    public static String formatCurrency(double amount, String currencyCode, String localeTag) {
        return FormatSupport.currency(amount, currencyCode, localeTag);
    }

    // ---- 日期（档位可省略，默认 MEDIUM）----

    /**
     * 日期格式化，档位默认 MEDIUM、locale 走四级优先级。
     *
     * @param value 时间点
     * @return 本地化字符串
     */
    public static String formatDate(Date value) {
        return formatDate(value, FormatSupport.DEFAULT_STYLE, null);
    }

    /**
     * 日期格式化，档位默认 MEDIUM。
     *
     * @param value     时间点
     * @param localeTag BCP 47 locale
     * @return 本地化字符串
     */
    public static String formatDate(Date value, String localeTag) {
        return formatDate(value, FormatSupport.DEFAULT_STYLE, localeTag);
    }

    /**
     * 日期格式化，locale 走四级优先级。
     *
     * @param value 时间点
     * @param style 风格档位；{@code null} → IAE
     * @return 本地化字符串
     */
    public static String formatDate(Date value, DateStyle style) {
        return formatDate(value, style, null);
    }

    /**
     * 日期格式化。
     *
     * @param value     时间点
     * @param style     风格档位；{@code null} → IAE
     * @param localeTag BCP 47 locale
     * @return 本地化字符串
     */
    public static String formatDate(Date value, DateStyle style, String localeTag) {
        return FormatSupport.date(value, style, localeTag);
    }

    // ---- 日期时间（档位可省略，默认 MEDIUM；时区为独立参数）----

    /**
     * 日期时间格式化，档位默认 MEDIUM、locale 走四级优先级。
     *
     * @param value 时间点
     * @param zone  时区（独立参数）；{@code null} / 非法 → IAE
     * @return 本地化字符串
     */
    public static String formatDateTime(Date value, TimeZone zone) {
        return formatDateTime(value, zone, FormatSupport.DEFAULT_STYLE, null);
    }

    /**
     * 日期时间格式化，档位默认 MEDIUM。
     *
     * @param value     时间点
     * @param zone      时区（独立参数）
     * @param localeTag BCP 47 locale
     * @return 本地化字符串
     */
    public static String formatDateTime(Date value, TimeZone zone, String localeTag) {
        return formatDateTime(value, zone, FormatSupport.DEFAULT_STYLE, localeTag);
    }

    /**
     * 日期时间格式化，locale 走四级优先级。
     *
     * @param value 时间点
     * @param zone  时区（独立参数）
     * @param style 风格档位；{@code null} → IAE
     * @return 本地化字符串
     */
    public static String formatDateTime(Date value, TimeZone zone, DateStyle style) {
        return formatDateTime(value, zone, style, null);
    }

    /**
     * 日期时间格式化。
     *
     * @param value     时间点
     * @param zone      时区（独立参数）；{@code null} / 非法 → IAE
     * @param style     风格档位；{@code null} → IAE
     * @param localeTag BCP 47 locale
     * @return 本地化字符串
     */
    public static String formatDateTime(Date value, TimeZone zone, DateStyle style, String localeTag) {
        return FormatSupport.dateTime(value, zone, style, localeTag);
    }
}
