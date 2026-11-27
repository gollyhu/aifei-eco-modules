/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.random.internal;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 安全随机取样的唯一实现点，持有唯一共享随机源与 62 字符取样表。
 */
public final class RandomEngine {

    private static final char[] CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomEngine() {
    }

    /**
     * 逐字符取样生成指定长度的随机串。
     *
     * @param length 字符数，必须大于等于 0
     * @return 随机串，长度为 0 时返回空串
     */
    public static String randomString(int length) {
        if (length < 0) {
            throw RandomErrors.negativeLength(length);
        }
        if (length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }
        return out.toString();
    }

    /**
     * 生成 32 字节随机令牌并编码为无填充的 Base64URL。
     *
     * @return 43 字符的 Base64URL 令牌
     */
    public static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
