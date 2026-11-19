/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 * 开发工程师 寇戴码（Enjoy 指令增量）
 */

package cn.hg.aifei.i18n.internal.directive;

/**
 * 页面级 lang / dir 覆盖的 ThreadLocal 唯一持有点（非承诺 API，设计 §4.1）。
 *
 * <p>与普通 {@code internal.LocaleContext}（请求级上下文 locale）刻意分离：本类承载「一次模板渲染期」的
 * 页面级默认值，由 {@code #lang('xxx')} / {@code #dir('xxx')} 设置，生命周期是一次 render。
 * 消费方须在渲染入口 try/finally 调 {@link #clear()}（渲染前先清、finally 再清），防线程池复用串页。</p>
 *
 * <p>普通 {@code ThreadLocal}（非 Inheritable），幂等清理；零业务语义、不写 LocaleContext。</p>
 */
public final class DirectivePageState {

    private static final ThreadLocal<String> PAGE_LANG_TL = new ThreadLocal<String>();
    private static final ThreadLocal<String> PAGE_DIR_TL = new ThreadLocal<String>();

    private DirectivePageState() {
    }

    /**
     * 设置页面级默认语言（调用方须先规范化）。
     *
     * @param localeTag 已规范化的 BCP 47 tag；null 等价于移除
     */
    public static void setPageLang(String localeTag) {
        if (localeTag == null) {
            PAGE_LANG_TL.remove();
        } else {
            PAGE_LANG_TL.set(localeTag);
        }
    }

    /**
     * @return 页面级默认语言 tag，未设置返回 null
     */
    public static String pageLang() {
        return PAGE_LANG_TL.get();
    }

    /**
     * 设置页面级方向覆盖，仅允许 ltr / rtl（校验在 DirDirective，本类只持有）。
     *
     * @param dir ltr / rtl；null 等价于移除
     */
    public static void setPageDirOverride(String dir) {
        if (dir == null) {
            PAGE_DIR_TL.remove();
        } else {
            PAGE_DIR_TL.set(dir);
        }
    }

    /**
     * @return 页面级方向覆盖，未设置返回 null
     */
    public static String pageDirOverride() {
        return PAGE_DIR_TL.get();
    }

    /**
     * 同时清空页面级 lang 与 dir；幂等，异常路径同样生效。
     */
    public static void clear() {
        PAGE_LANG_TL.remove();
        PAGE_DIR_TL.remove();
    }
}
