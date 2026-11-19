/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

import cn.hg.aifei.i18n.internal.I18nHolder;
import cn.hg.aifei.i18n.internal.I18nEngine;
import cn.hg.aifei.i18n.internal.LocaleNormalizer;

/**
 * 显式 locale / 默认取文案 / 整包导出 / 刷新的公共静态门面。
 *
 * <p>有状态门面：{@code final} + 私有构造、零实例字段；全部方法经 {@link I18nHolder} 取当前引擎，
 * 未装配 / 已停止 → {@link I18nException}，不静默返回 {@code null}。</p>
 *
 * <p>门面转发，{@code key} 校验与规则均在 engine/规则类唯一定义点（设计 §6.3），本类不重复实现。</p>
 */
public final class I18nKit {

    private I18nKit() {
    }

    /**
     * 显式 locale 的唯一入口：当场规范化 {@code localeTag} 并返回不可变 {@link Localizer}。
     *
     * @param localeTag BCP 47 locale（可下划线写法，先规范化）
     * @return 绑定已规范化 tag 的链式载体
     * @throws IllegalArgumentException {@code localeTag} 为 {@code null} / 空白 / 非法 BCP 47（§8.3 逐字）
     */
    public static Localizer local(String localeTag) {
        return new Localizer(LocaleNormalizer.normalize(localeTag));
    }

    /**
     * 默认 local 入口取文案：locale 走四级优先级解析；{@code {0}} 位置插值，缺参占位符原样保留、不抛；
     * {@code args} 为 {@code null} 或空数组等价无参数。
     *
     * @param key  文案 key，非空 / 非空白（校验由 {@link I18nEngine} 统一抛）
     * @param args 插值参数，可为 {@code null} / 空数组
     * @return 译文（未命中返回 key 原文）
     */
    public static String message(String key, Object... args) {
        return I18nHolder.engine().message(key, null, args);
    }

    /**
     * 整包导出：显式 locale，不受上下文影响；不可列举来源跳过 + WARN；{@code dir} 不可用时降级 {@code ltr} + WARN。
     *
     * @param localeTag BCP 47 locale（显式非法 → IAE）
     * @return 不可变语言包
     * @throws IllegalArgumentException localeTag 显式非法
     */
    public static LanguageBundle bundle(String localeTag) {
        return I18nHolder.engine().bundle(localeTag);
    }

    /**
     * 显式全清所有来源的本地缓存（db/kv 懒缓存与缓存层）；{@code properties} 资源仍须重启才变。
     * 幂等，空来源刷新无副作用。
     */
    public static void refresh() {
        I18nHolder.engine().refresh();
    }
}
