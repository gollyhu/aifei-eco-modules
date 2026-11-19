/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.internal;

import cn.hg.aifei.i18n.I18nException;

/**
 * 运行期引擎持有的唯一定义点（设计 §6.2 / §5.1）。
 *
 * <p>{@code I18nKit} / {@code FormatKit} 经本类取引擎；未装配 / 已停止 → {@link I18nException}
 * （消息逐字：{@code i18n 未装配或已停止，请先注册 I18nPlugin}）。</p>
 */
public final class I18nHolder {

    private static volatile I18nEngine engine;

    private I18nHolder() {
    }

    /**
     * 安装引擎（装配入口 {@code I18nPlugin#start()} 调用）。
     *
     * @param target 引擎实例
     */
    public static void install(I18nEngine target) {
        engine = target;
    }

    /**
     * @return 当前引擎
     * @throws I18nException 未装配或已停止
     */
    public static I18nEngine engine() {
        I18nEngine current = engine;
        if (current == null) {
            throw new I18nException("i18n 未装配或已停止，请先注册 I18nPlugin");
        }
        return current;
    }

    /**
     * 清除引擎；幂等。
     */
    public static void clear() {
        engine = null;
    }
}
