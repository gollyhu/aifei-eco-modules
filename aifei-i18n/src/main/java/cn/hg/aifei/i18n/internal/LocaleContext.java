/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.internal;

/**
 * 上下文 Locale 的 ThreadLocal 唯一持有点（设计 D-12 / Q 同构 aifei-trace）。
 *
 * <p>普通 {@code ThreadLocal}（非 {@code InheritableThreadLocal}），避免线程池复用线程串语言；
 * 只做 set / get / clear，零业务语义。请求结束必须由 {@code web.I18nHandler} 在 finally 中清理。</p>
 */
public final class LocaleContext {

    private static final ThreadLocal<String> LOCALE_TL = new ThreadLocal<String>();

    private LocaleContext() {
    }

    /**
     * 绑定当前线程的 locale（应已规范化）。
     *
     * @param localeTag 已规范化的 locale tag；{@code null} 等价于移除
     */
    public static void set(String localeTag) {
        if (localeTag == null) {
            LOCALE_TL.remove();
        } else {
            LOCALE_TL.set(localeTag);
        }
    }

    /**
     * @return 当前线程绑定的 locale tag，未绑定返回 {@code null}
     */
    public static String get() {
        return LOCALE_TL.get();
    }

    /**
     * 清理当前线程的 locale，防线程池复用串号；对未绑定线程幂等安全。
     */
    public static void clear() {
        LOCALE_TL.remove();
    }
}
