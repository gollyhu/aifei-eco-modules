/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.random.internal;

import cn.hg.aifei.tools.random.RandomException;

/**
 * random 异常消息与异常构造的唯一定义点。
 */
public final class RandomErrors {

    private RandomErrors() {
    }

    /**
     * 请求长度为负数。
     *
     * @param n 实际长度
     * @return 统一异常
     */
    public static RandomException negativeLength(int n) {
        return new RandomException("random: length must not be negative, but was: " + n);
    }
}
