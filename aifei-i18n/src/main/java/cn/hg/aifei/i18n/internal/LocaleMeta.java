/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.internal;

import cn.aifei.log.Log;
import java.awt.ComponentOrientation;
import java.util.Locale;

/**
 * {@code localeName} 与 {@code dir} 判定的唯一定义点。
 *
 * <p>{@code localeName} 取 JDK {@link Locale#getDisplayName(Locale)}（该语言在该语言中的名称），
 * 禁语言名译名表；{@code dir} 只走 JDK 公开 API {@link ComponentOrientation}，
 * 禁自建语言码→方向映射表。</p>
 *
 * <p>判定能力不可用（{@code java.desktop} 被 jlink 裁剪等）时降级 {@code ltr} + WARN，
 * 不阻断导出、不改 JSON 形状。可通过 {@link #setDirectionResolver} 注入替身构造降级用例。</p>
 */
public final class LocaleMeta {

    /**
     * 书写方向判定器（函数式接口，仅用于测试隔离；生产环境默认为 JDK 判定）。
     */
    public interface DirectionResolver {
        /**
         * @param locale 目标 locale
         * @return {@code ltr} 或 {@code rtl}
         */
        String resolve(Locale locale);
    }

    private static volatile DirectionResolver resolver = new JdkDirectionResolver();

    private LocaleMeta() {
    }

    /**
     * 该语言在该语言中的名称（JDK 内置能力）。
     *
     * @param locale 目标 locale
     * @return 本地化名称
     */
    public static String localeName(Locale locale) {
        return locale.getDisplayName(locale);
    }

    /**
     * 书写方向：JDK 公开 API 判定，能力不可用时降级 {@code ltr} + WARN。
     *
     * @param locale 目标 locale
     * @return {@code ltr} 或 {@code rtl}
     */
    public static String dir(Locale locale) {
        try {
            return resolver.resolve(locale);
        } catch (LinkageError | RuntimeException e) {
            warn(locale, e);
            return "ltr";
        }
    }

    /**
     * 替换方向判定器（测试隔离用）；传 {@code null} 恢复 JDK 默认判定。
     *
     * @param newResolver 新的判定器，可为 {@code null}
     */
    public static void setDirectionResolver(DirectionResolver newResolver) {
        resolver = newResolver == null ? new JdkDirectionResolver() : newResolver;
    }

    private static final class JdkDirectionResolver implements DirectionResolver {
        @Override
        public String resolve(Locale locale) {
            return ComponentOrientation.getOrientation(locale).isLeftToRight() ? "ltr" : "rtl";
        }
    }

    private static void warn(Locale locale, Throwable cause) {
        try {
            Log log = Log.get(LocaleMeta.class);
            log.warn("i18n dir 判定能力不可用，已降级为 ltr: locale=" + locale + ", 原因=" + cause);
        } catch (Throwable ignored) {
            // 日志通道不可用不影响降级
        }
    }
}
