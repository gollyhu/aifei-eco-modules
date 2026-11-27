/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.mask.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.hg.aifei.tools.mask.MaskUtil;

/**
 * 九条默认脱敏规则的唯一初始注册点（冻结契约）。
 *
 * <p>规则名：phone / idCard / bankCard / email / name / fixedPhone / password / address / ip。
 * 通用打码算法本身不在此复制，一律由 {@link MaskUtil#mask(String, int, int)} 承载。</p>
 */
public final class DefaultMasks {

    private DefaultMasks() {
    }

    /**
     * 把九条默认规则装入注册表；由 MaskRegistry 静态初始化调用一次。
     */
    static void install() {
        MaskRegistry.register("phone", v -> MaskUtil.mask(v, 3, 4));
        MaskRegistry.register("idCard", v -> MaskUtil.mask(v, 6, 4));
        MaskRegistry.register("bankCard", v -> MaskUtil.mask(v, 6, 4));
        MaskRegistry.register("email", DefaultMasks::maskEmail);
        MaskRegistry.register("name", v -> MaskUtil.mask(v, 1, 0));
        MaskRegistry.register("fixedPhone", DefaultMasks::maskFixedPhone);
        MaskRegistry.register("password", v -> v == null || v.isEmpty() ? v : "******");
        MaskRegistry.register("address", DefaultMasks::maskAddress);
        MaskRegistry.register("ip", DefaultMasks::maskIp);
    }

    private static String maskEmail(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int at = value.indexOf('@');
        if (at < 0) {
            return MaskUtil.mask(value, 1, 1);
        }
        return MaskUtil.mask(value.substring(0, at), 1, 1) + value.substring(at);
    }

    private static String maskFixedPhone(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int dash = value.lastIndexOf('-');
        if (dash < 0) {
            return MaskUtil.mask(value, 4, 4);
        }
        String prefix = value.substring(0, dash + 1);
        return prefix + MaskUtil.mask(value.substring(dash + 1), 0, 4);
    }

    private static String maskAddress(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= 6) {
            return MaskUtil.mask(value, 0, 0);
        }
        return MaskUtil.mask(value, 6, 0);
    }

    private static String maskIp(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.indexOf('.') >= 0) {
            return maskSegments(value, "\\.");
        }
        if (value.indexOf(':') >= 0) {
            return maskSegments(ipv6Parts(value), ":");
        }
        return MaskUtil.mask(value, 1, 0);
    }

    /** IPv6 的 :: 展开为完整八段，保证一段对应一个掩码（对齐设计给定的字面示例）。 */
    private static String[] ipv6Parts(String value) {
        int doubleColon = value.indexOf("::");
        if (doubleColon < 0) {
            return value.split(":", -1);
        }
        String head = value.substring(0, doubleColon);
        String tail = value.substring(doubleColon + 2);
        List<String> headParts = head.isEmpty()
                ? new ArrayList<String>() : new ArrayList<String>(Arrays.asList(head.split(":", -1)));
        List<String> tailParts = tail.isEmpty()
                ? new ArrayList<String>() : new ArrayList<String>(Arrays.asList(tail.split(":", -1)));
        List<String> parts = new ArrayList<String>(headParts);
        int zeros = 8 - headParts.size() - tailParts.size();
        for (int i = 0; i < zeros; i++) {
            parts.add("0");
        }
        parts.addAll(tailParts);
        return parts.toArray(new String[parts.size()]);
    }

    private static String maskSegments(String value, String separator) {
        return maskSegments(value.split(separator, -1), ":".equals(separator) ? ":" : ".");
    }

    private static String maskSegments(String[] parts, String literal) {
        if (parts.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            out.append(literal).append('*');
        }
        return out.toString();
    }
}
