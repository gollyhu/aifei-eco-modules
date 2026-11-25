/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.internal;

import cn.hg.aifei.i18n.I18nException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * i18n 配置域：{@code i18n.*} 四个键的键名、默认值、解析与校验的唯一定义点（设计 §7）。
 *
 * <p>其余类不得手写键名字面量，一律引用本类常量；无启用开关、无第五键；档位 MEDIUM 硬编码不占键。</p>
 */
public final class I18nConfig {

    /** 回退链终点 locale，必填。 */
    public static final String KEY_DEFAULT_LOCALE = "i18n.defaultLocale";
    /** 受支持语种列表（请求头白名单与导出合法性判断）。 */
    public static final String KEY_LOCALES = "i18n.locales";
    /** 来源稳定名有序清单。 */
    public static final String KEY_SOURCES = "i18n.sources";
    /** 缓存 TTL（毫秒）。 */
    public static final String KEY_CACHE_TTL = "i18n.cache.ttlMillis";

    /** 未配置 sources 时的默认来源（零第三方、开箱可用）。 */
    public static final String DEFAULT_SOURCE = "properties";
    /** 未配置 ttlMillis 时的默认 TTL。 */
    public static final long DEFAULT_CACHE_TTL_MILLIS = 60000L;

    private final String defaultLocale;
    private final List<String> locales;
    private final List<String> sources;
    private final long cacheTtlMillis;

    private I18nConfig(String defaultLocale, List<String> locales, List<String> sources, long cacheTtlMillis) {
        this.defaultLocale = defaultLocale;
        this.locales = locales;
        this.sources = sources;
        this.cacheTtlMillis = cacheTtlMillis;
    }

    /**
     * 解析并校验配置；任一键非法即失败，不产出半成品。
     *
     * @param properties 配置源，可为 {@code null}（等价全默认，但 defaultLocale 仍必填 → 失败）
     * @return 配置值对象
     * @throws I18nException defaultLocale 缺失 / 任一配置非法（消息含键名与原值）
     */
    public static I18nConfig parse(Properties properties) {
        Properties source = properties == null ? new Properties() : properties;

        String rawDefault = source.getProperty(KEY_DEFAULT_LOCALE);
        if (rawDefault == null || rawDefault.trim().isEmpty()) {
            throw new I18nException("缺少必填配置项 " + KEY_DEFAULT_LOCALE);
        }
        final String defaultLocale;
        try {
            defaultLocale = LocaleNormalizer.normalize(rawDefault);
        } catch (IllegalArgumentException e) {
            throw new I18nException("配置项 " + KEY_DEFAULT_LOCALE + " 非法: " + rawDefault + "（不是合法 BCP 47）", e);
        }

        List<String> locales = parseLocales(source.getProperty(KEY_LOCALES));
        List<String> sources = parseSources(source.getProperty(KEY_SOURCES));
        long ttl = parseTtl(source.getProperty(KEY_CACHE_TTL));

        return new I18nConfig(defaultLocale, locales, sources, ttl);
    }

    /**
     * @return 回退链终点 locale（已规范化），必非 {@code null}
     */
    public String defaultLocale() {
        return defaultLocale;
    }

    /**
     * @return 受支持语种白名单（已规范化）；空列表 = 不设白名单
     */
    public List<String> locales() {
        return locales;
    }

    /**
     * @return 来源稳定名有序清单，至少含一个
     */
    public List<String> sources() {
        return sources;
    }

    /**
     * @return 缓存 TTL（毫秒）；{@code ≤ 0} 表示不缓存
     */
    public long cacheTtlMillis() {
        return cacheTtlMillis;
    }

    private static List<String> parseLocales(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        for (String part : raw.split(",")) {
            String tag = part.trim();
            if (tag.isEmpty()) {
                continue;
            }
            String normalized = LocaleNormalizer.tryNormalize(tag);
            if (normalized == null) {
                throw new I18nException("配置项 " + KEY_LOCALES + " 非法: " + raw + "（含非法 BCP 47 片段）");
            }
            result.add(normalized);
        }
        return Collections.unmodifiableList(result);
    }

    private static List<String> parseSources(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.singletonList(DEFAULT_SOURCE);
        }
        List<String> result = new ArrayList<String>();
        for (String part : raw.split(",")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            result.add(name);
        }
        if (result.isEmpty()) {
            return Collections.singletonList(DEFAULT_SOURCE);
        }
        return Collections.unmodifiableList(result);
    }

    private static long parseTtl(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_CACHE_TTL_MILLIS;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new I18nException("配置项 " + KEY_CACHE_TTL + " 非法: " + raw + "（要求整数）", e);
        }
    }
}
