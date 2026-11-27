/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.encrypt;

import cn.hg.aifei.tools.random.RandomException;

/**
 * 随机字符串工具（AES 报文的 16 字节随机头、安全模式回复的 nonce）
 */
public class RandomUtil {

    private static final String MISSING_TOOLS =
            "缺少可选依赖 io.github.gollyhu:aifei-tools:1.1.0-SNAPSHOT；使用本能力时消费方须显式声明";

    /**
     * 微信官方约定的 16 字节随机串（等价于 16 个 ASCII 字符）
     */
    public static String randomStr() {
        return randomStr(16);
    }

    public static String randomStr(int length) {
        return randomStrViaTools(length);
    }

    private static String randomStrViaTools(int length) {
        try {
            return RandomHolder.randomString(length);
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException(MISSING_TOOLS, e);
        }
    }

    /**
     * 隔离 tools 异常类型引用：随机源定义点只在 tools random，本模块不保留字符表与取样器。
     */
    private static final class RandomHolder {
        private static String randomString(int length) {
            try {
                return cn.hg.aifei.tools.random.RandomUtil.randomString(length);
            } catch (RandomException e) {
                throw new NegativeArraySizeException();
            }
        }
    }

    private RandomUtil() {
    }
}
