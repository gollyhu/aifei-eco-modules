/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.internal;

import cn.hg.aifei.i18n.DateStyle;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 格式化薄封装的唯一定义点。
 *
 * <p>把生效 Locale 交给 JDK 的 {@link NumberFormat} / {@link DateFormat} / {@link Currency}，
 * 模块零自建规则数据（无模式串、无货币符号表、无小数位表、无方向表）。locale 解析复用
 * {@link LocaleResolver#resolve(String)} 同一实现，档位映射是本类唯一的枚举→JDK 常量表。</p>
 */
public final class FormatSupport {

    /** 默认风格档位：硬编码契约，不占配置键。 */
    public static final DateStyle DEFAULT_STYLE = DateStyle.MEDIUM;

    private FormatSupport() {
    }

    /**
     * 数值格式化，分隔符随 locale。
     *
     * @param value     数值
     * @param localeTag 显式 locale（可为 {@code null}，走四级优先级）
     * @return 本地化字符串
     */
    public static String number(double value, String localeTag) {
        return NumberFormat.getNumberInstance(localeOf(localeTag)).format(value);
    }

    /**
     * 百分比格式化（{@code 0.85 → 85%}），写法随 locale。
     *
     * @param ratio     比例值
     * @param localeTag 显式 locale（可为 {@code null}）
     * @return 本地化字符串
     */
    public static String percent(double ratio, String localeTag) {
        return NumberFormat.getPercentInstance(localeOf(localeTag)).format(ratio);
    }

    /**
     * 货币格式化：币种独立参数，小数位由币种决定，符号 / 位置由 locale 决定。
     *
     * @param amount       金额
     * @param currencyCode 币种代码（如 {@code USD}），独立参数
     * @param localeTag    显式 locale（可为 {@code null}）
     * @return 本地化字符串
     * @throws IllegalArgumentException currencyCode 为 {@code null}（本类统一口径）或非法（JDK 透传）
     */
    public static String currency(double amount, String currencyCode, String localeTag) {
        if (currencyCode == null) {
            throw new IllegalArgumentException("币种代码不能为 null");
        }
        Currency currency = Currency.getInstance(currencyCode.trim());
        NumberFormat format = NumberFormat.getCurrencyInstance(localeOf(localeTag));
        format.setCurrency(currency);
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits >= 0) {
            format.setMinimumFractionDigits(fractionDigits);
            format.setMaximumFractionDigits(fractionDigits);
        }
        return format.format(amount);
    }

    /**
     * 日期格式化，档位与 locale 正交。
     *
     * @param value     时间点
     * @param style     风格档位；{@code null} → IAE
     * @param localeTag 显式 locale（可为 {@code null}）
     * @return 本地化字符串
     * @throws IllegalArgumentException value / style 为 {@code null}
     */
    public static String date(Date value, DateStyle style, String localeTag) {
        if (value == null) {
            throw new IllegalArgumentException("值不能为 null");
        }
        if (style == null) {
            throw new IllegalArgumentException("档位不能为 null");
        }
        return DateFormat.getDateInstance(toJdkStyle(style), localeOf(localeTag)).format(value);
    }

    /**
     * 日期时间格式化：时区是独立参数，与 locale 正交；非法时区（如 {@code Mars/Base}）→ IAE。
     *
     * @param value     时间点
     * @param zone      时区（独立参数）；{@code null} / 非法 → IAE
     * @param style     风格档位；{@code null} → IAE
     * @param localeTag 显式 locale（可为 {@code null}）
     * @return 本地化字符串
     * @throws IllegalArgumentException value / zone / style 为 {@code null}，或时区非法
     */
    public static String dateTime(Date value, TimeZone zone, DateStyle style, String localeTag) {
        if (value == null) {
            throw new IllegalArgumentException("值不能为 null");
        }
        if (zone == null) {
            throw new IllegalArgumentException("时区不能为 null");
        }
        if (style == null) {
            throw new IllegalArgumentException("档位不能为 null");
        }
        String zoneId = zone.getID();
        try {
            ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("时区非法: " + zoneId, e);
        }
        DateFormat format = DateFormat.getDateTimeInstance(toJdkStyle(style), toJdkStyle(style), localeOf(localeTag));
        format.setTimeZone(zone);
        return format.format(value);
    }

    /**
     * 档位枚举 → JDK {@link DateFormat} 常量（唯一映射定义点）；{@code null} → IAE。
     *
     * @param style 档位
     * @return JDK 风格常量
     * @throws IllegalArgumentException style 为 {@code null}
     */
    public static int toJdkStyle(DateStyle style) {
        if (style == null) {
            throw new IllegalArgumentException("档位不能为 null");
        }
        switch (style) {
            case SHORT:
                return DateFormat.SHORT;
            case MEDIUM:
                return DateFormat.MEDIUM;
            case LONG:
                return DateFormat.LONG;
            case FULL:
                return DateFormat.FULL;
            default:
                throw new IllegalArgumentException("档位不能为 null");
        }
    }

    private static Locale localeOf(String localeTag) {
        return Locale.forLanguageTag(LocaleResolver.resolve(localeTag));
    }
}
