/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

import cn.hg.aifei.i18n.internal.I18nEngine;
import cn.hg.aifei.i18n.internal.I18nHolder;

/**
 * 取文案 / 整包导出 / 刷新的公共静态门面。
 *
 * <p>有状态门面：{@code final} + 私有构造、零实例字段；全部方法经 {@link I18nHolder} 取当前引擎，
 * 未装配 / 已停止 → {@link I18nException}，不静默返回 {@code null}。</p>
 *
 * <p>本类只做参数校验 + 转发，不重复实现回退 / 解析 / 排序（设计 §6.3 唯一定义点纪律）。</p>
 */
public final class I18nKit {

    private I18nKit() {
    }

    /**
     * 取文案：locale 走 {@code LocaleResolver} 四级优先级解析（显式缺失时）。
     *
     * @param key 文案 key，非空 / 非空白
     * @return 译文（未命中返回 key 原文）
     * @throws IllegalArgumentException key 为空 / 空白
     */
    public static String message(String key) {
        return message(key, null);
    }

    /**
     * 取文案。
     *
     * @param key       文案 key，非空 / 非空白
     * @param localeTag BCP 47 locale（可下划线写法，先规范化）；显式非法 → IAE
     * @return 译文（未命中返回 key 原文）
     * @throws IllegalArgumentException key 为空 / 空白，或 localeTag 显式非法
     */
    public static String message(String key, String localeTag) {
        return message(key, localeTag, new Object[0]);
    }

    /**
     * 取文案 + {@code {0}} 位置插值：缺参占位符原样保留、不抛；{@code args} 为 {@code null} 或空数组等价无参数。
     *
     * @param key       文案 key，非空 / 非空白
     * @param localeTag BCP 47 locale（可下划线写法，先规范化）；显式非法 → IAE
     * @param args      插值参数，可为 {@code null} / 空数组
     * @return 译文（未命中返回 key 原文）
     * @throws IllegalArgumentException key 为空 / 空白，或 localeTag 显式非法
     */
    public static String message(String key, String localeTag, Object... args) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key 不能为空");
        }
        I18nEngine engine = I18nHolder.engine();
        return engine.message(key, localeTag, args);
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
