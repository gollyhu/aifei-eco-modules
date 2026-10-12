/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.encrypt;

import java.security.SecureRandom;

/**
 * 随机字符串工具（AES 报文的 16 字节随机头、安全模式回复的 nonce）
 */
public class RandomKit {

    private static final char[] CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 微信官方约定的 16 字节随机串（等价于 16 个 ASCII 字符）
     */
    public static String randomStr() {
        return randomStr(16);
    }

    public static String randomStr(int length) {
        StringBuilder ret = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            ret.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }
        return ret.toString();
    }

    private RandomKit() {
    }
}
