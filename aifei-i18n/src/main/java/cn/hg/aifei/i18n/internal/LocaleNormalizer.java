/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.internal;

import java.util.IllformedLocaleException;
import java.util.Locale;

/**
 * locale 规范化 + 严格 BCP 47 校验的唯一定义点（设计 D-7，实证 E-2）。
 *
 * <p>规则：{@code _} 归一为 {@code -}、语言码小写、地区码大写、脚本码首字母大写；
 * 基于 Java 8 {@code Locale.Builder#setLanguageTag}（严格）实现，一次性覆盖全部规范化规则与非法校验。</p>
 *
 * <p>切勿用 {@code Locale.forLanguageTag} 做校验——它是宽松解析，会把 {@code zh_CN_} 之类的非法片段
 * 静默接受为空 locale。</p>
 */
public final class LocaleNormalizer {

    private LocaleNormalizer() {
    }

    /**
     * 严格规范化：非法即抛 IAE（显式入参路径，Q8）。
     *
     * @param localeTag 原始 locale tag，非空
     * @return 规范化后的 BCP 47 tag（如 {@code zh_CN → zh-CN}、{@code zh-hant-tw → zh-Hant-TW}）
     * @throws IllegalArgumentException {@code localeTag} 为 {@code null} / 空白 / 非法 BCP 47
     */
    public static String normalize(String localeTag) {
        if (localeTag == null || localeTag.trim().isEmpty()) {
            throw new IllegalArgumentException("locale 非法: " + localeTag + "（不是合法 BCP 47）");
        }
        String dashed = localeTag.trim().replace('_', '-');
        try {
            return new Locale.Builder().setLanguageTag(dashed).build().toLanguageTag();
        } catch (IllformedLocaleException e) {
            throw new IllegalArgumentException("locale 非法: " + localeTag + "（不是合法 BCP 47）", e);
        }
    }

    /**
     * 容错规范化：非法返回 {@code null} 而不抛（Accept-Language 请求头外部输入路径，Q8）。
     *
     * @param localeTag 原始 locale 片段，可为 {@code null}
     * @return 规范化后的 BCP 47 tag；非法 / 空白 / {@code null} 返回 {@code null}
     */
    public static String tryNormalize(String localeTag) {
        if (localeTag == null || localeTag.trim().isEmpty()) {
            return null;
        }
        String dashed = localeTag.trim().replace('_', '-');
        try {
            return new Locale.Builder().setLanguageTag(dashed).build().toLanguageTag();
        } catch (IllformedLocaleException e) {
            return null;
        }
    }
}
