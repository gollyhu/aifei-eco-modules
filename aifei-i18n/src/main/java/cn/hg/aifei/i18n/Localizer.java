/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

import cn.hg.aifei.i18n.internal.I18nHolder;

/**
 * 不可变链式载体：绑定一个已规范化 locale，提供取文案入口。
 *
 * <p>由 {@link I18nKit#local(String)} 当场规范化后经包级构造创建，持有唯一 {@code final} 字段，
 * 可复用、可跨线程共享；两方法均纯转发 {@link cn.hg.aifei.i18n.internal.I18nEngine#message}，
 * 回退 / 规范化 / 插值规则各有唯一定义点，本类零复制。</p>
 */
public final class Localizer {

    private final String localeTag;

    Localizer(String normalizedLocaleTag) {
        this.localeTag = normalizedLocaleTag;
    }

    /**
     * 取文案：无插值参数。
     *
     * @param key 文案 key，非空 / 非空白（校验由 engine 统一抛）
     * @return 译文（未命中返回 key 原文）
     */
    public String message(String key) {
        return message(key, new Object[0]);
    }

    /**
     * 取文案 + {@code {0}} 位置插值：缺参占位符原样保留、不抛；{@code args} 为 {@code null} 或空数组等价无参数。
     *
     * @param key  文案 key，非空 / 非空白（校验由 engine 统一抛）
     * @param args 插值参数，可为 {@code null} / 空数组
     * @return 译文（未命中返回 key 原文）
     */
    public String message(String key, Object... args) {
        return I18nHolder.engine().message(key, this.localeTag, args);
    }
}
