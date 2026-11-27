/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.crypto.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 常量时间比较的唯一实现点，null 入参不抛异常。
 */
public final class ConstantTimeEngine {

    private ConstantTimeEngine() {
    }

    /**
     * 按 UTF-8 字节逐位比较两个文本，耗时不随相同前缀长度变化。
     *
     * @param expected 期望文本，允许为 null
     * @param actual 实际文本，允许为 null
     * @return 两者均非 null 且字节逐位相等时返回 true，否则返回 false
     */
    public static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
