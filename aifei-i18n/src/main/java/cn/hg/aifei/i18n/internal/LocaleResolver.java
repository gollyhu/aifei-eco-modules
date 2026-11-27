/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.internal;

import java.util.List;

/**
 * 生效 locale 四级优先级 + Accept-Language 解析的唯一定义点。
 *
 * <p>优先级：显式传参 &gt; 上下文 Locale &gt; 请求头 Accept-Language &gt; {@code i18n.defaultLocale}。
 * 取文案与格式化路径共用本类的同一实现，不得另写一套解析。</p>
 *
 * <p>请求头级由 {@code web.I18nHandler} 解析后写入 {@link LocaleContext}，故本类 {@link #resolve(String)}
 * 只需处理「显式 &gt; 上下文 &gt; 默认」三级；请求头的解析落在 {@link #parseAcceptLanguage(String)}。</p>
 */
public final class LocaleResolver {

    private LocaleResolver() {
    }

    /**
     * 三级解析（格式化与取文案共用）：显式 &gt; 上下文 &gt; 默认 locale。
     *
     * <p>显式值走严格规范化（非法抛 IAE）；上下文值由 Web 入口写入，已规范化，直接采用；
     * 缺省落 {@code config.defaultLocale}（已规范化），不抛异常。</p>
     *
     * @param explicit 显式 locale（可为 {@code null} / 下划线写法）
     * @return 生效的规范化 locale tag
     * @throws IllegalArgumentException 显式 locale 非法
     */
    public static String resolve(String explicit) {
        if (explicit != null) {
            return LocaleNormalizer.normalize(explicit);
        }
        String context = LocaleContext.get();
        if (context != null) {
            return context;
        }
        return I18nHolder.engine().config().defaultLocale();
    }

    /**
     * 解析 Accept-Language 原始头，返回首个受支持的规范化语种（忽略 q 权重）。
     *
     * <p>逗号分段，每段取 {@code ;} 之前的部分；非法片段静默忽略（{@code tryNormalize}）；
     * 配置了白名单时只接受白名单内的语种；全部不可用时返回 {@code null}（由调用方落 default）。</p>
     *
     * @param raw 原始 Accept-Language 头（可 {@code null}）
     * @return 首个受支持的规范化 locale tag；无可用片段返回 {@code null}
     */
    public static String parseAcceptLanguage(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        List<String> whitelist = I18nHolder.engine().config().locales();
        for (String segment : raw.split(",")) {
            String tag = segment.split(";")[0].trim();
            String normalized = LocaleNormalizer.tryNormalize(tag);
            if (normalized == null) {
                continue;
            }
            if (whitelist.isEmpty() || whitelist.contains(normalized)) {
                return normalized;
            }
        }
        return null;
    }
}
