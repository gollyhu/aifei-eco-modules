/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.random;

import cn.hg.aifei.tools.random.internal.RandomEngine;

/**
 * 安全随机字符串与令牌的无状态工具，全部方法均为静态方法。
 *
 * <p>随机源为 JDK 保证线程安全的 {@code SecureRandom}，本类没有实例字段，
 * 可多线程并发调用；失败统一抛 {@link RandomException}，公共方法不声明受检异常。</p>
 */
public final class RandomUtil {

    private RandomUtil() {
    }

    /**
     * 生成指定长度的 ASCII 安全随机串，字符集为 62 个字母与数字。
     *
     * @param length 字符数，必须大于等于 0；为 0 时返回空串
     * @return 仅含 {@code A-Z a-z 0-9} 的随机串
     * @throws RandomException length 为负数
     */
    public static String randomString(int length) {
        return RandomEngine.randomString(length);
    }

    /**
     * 生成 32 字节安全随机令牌，输出无填充的 Base64URL 字符串（43 字符）。
     *
     * @return 43 字符的 Base64URL 令牌，不含等号填充
     */
    public static String token() {
        return RandomEngine.token();
    }
}
