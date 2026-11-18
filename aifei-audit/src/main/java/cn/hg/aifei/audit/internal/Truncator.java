/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

/**
 * 脱敏与截断唯一实现点（非承诺 API）：按参数位剔除 + 长度截断（超限截断而非抛错）。
 *
 * <p>摘要形如 {@code #0=visible;#1=<masked>}，不用 JSON：无需转义逻辑，且「哪些位被剔除」一眼可验。</p>
 */
public final class Truncator {

    static final String MASKED = "<masked>";

    static final String INJECTED = "<injected>";

    static final String NULL_VALUE = "<null>";

    static final String ARGS_OMITTED = "[args omitted]";

    static final String ELLIPSIS = "...";

    private Truncator() {
    }

    /**
     * @param args      业务方法实参，可为 null 或空
     * @param mask      剔除的参数位（0-based），可为 null
     * @param maxLength 摘要长度上限
     * @return 摘要；无入参时为 {@code null}（不记录）
     */
    public static String summary(Object[] args, int[] mask, int maxLength) {
        if (args == null || args.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append(';');
            }
            builder.append('#').append(i).append('=');
            builder.append(isMasked(mask, i) ? MASKED : render(args[i]));
        }
        return truncate(builder.toString(), maxLength);
    }

    /**
     * @param text      原文，可为 null
     * @param maxLength 长度上限；{@code 0} 表示只留占位符
     * @return 截断后的文本
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (maxLength <= 0) {
            return ARGS_OMITTED;
        }
        if (text.length() > maxLength) {
            if (maxLength <= ELLIPSIS.length()) {
                return text.substring(0, maxLength);
            }
            return text.substring(0, maxLength - ELLIPSIS.length()) + ELLIPSIS;
        }
        return text;
    }

    private static String render(Object arg) {
        return arg == null ? NULL_VALUE : String.valueOf(arg);
    }

    private static boolean isMasked(int[] mask, int index) {
        if (mask == null) {
            return false;
        }
        for (int i = 0; i < mask.length; i++) {
            if (mask[i] == index) {
                return true;
            }
        }
        return false;
    }
}
