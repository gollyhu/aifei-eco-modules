/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.internal;

import java.text.MessageFormat;

/**
 * {@code {0}} 位置占位的唯一插值定义点。
 *
 * <p>委派 JDK {@link MessageFormat}：缺参占位符原样保留、不抛；整包导出下发原文、不经本类。</p>
 */
public final class MessageInterpolator {

    private MessageInterpolator() {
    }

    /**
     * 插值：用 {@code args} 替换 {@code pattern} 中的 {@code {0}}{@code {1}} 位置占位符。
     *
     * @param pattern 文案原文（含占位符）
     * @param args    位置参数，可为 {@code null} / 空数组（等价无参数）
     * @return 插值后的字符串；参数不足时缺参占位符原样保留；插值异常时退回原文，绝不因文案内容而 500
     */
    public static String format(String pattern, Object... args) {
        if (pattern == null) {
            return null;
        }
        Object[] actual = args == null ? new Object[0] : args;
        if (actual.length == 0) {
            return pattern;
        }
        try {
            return MessageFormat.format(pattern, actual);
        } catch (IllegalArgumentException e) {
            // 文案原文格式异常（如孤立的单引号/花括号）时退回原文，不向业务抛
            return pattern;
        }
    }
}
