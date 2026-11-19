/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 * 开发工程师 寇戴码（Enjoy 指令增量）
 */

package cn.hg.aifei.i18n.internal.directive;

import cn.aifei.enjoy.Engine;

/**
 * 三条 Enjoy 指令的集中注册入口（非承诺 API）。
 *
 * <p>消费方构造 {@link Engine} 后调用一次 {@link #register(Engine)} 即完成 i18n / lang / dir 注册。
 * {@code keepLineBlank=true} 保留指令独占行排版，避免文案粘连。</p>
 */
public final class DirectiveRegistrar {

    private DirectiveRegistrar() {
    }

    /**
     * 向引擎注册 i18n / lang / dir 三条指令。
     *
     * @param engine Enjoy 引擎，非空
     * @throws IllegalArgumentException engine 为 null
     */
    public static void register(Engine engine) {
        if (engine == null) {
            throw new IllegalArgumentException("engine 不能为 null");
        }
        engine.addDirective("i18n", I18nDirective.class, true);
        engine.addDirective("lang", LangDirective.class, true);
        engine.addDirective("dir", DirDirective.class, true);
    }
}
