/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha.internal;

import cn.hg.aifei.tools.captcha.CaptchaException;

/**
 * captcha 异常消息与构造的唯一定义点（措辞唯一来源）。
 */
public final class CaptchaErrors {

    private CaptchaErrors() {
    }

    /**
     * 配置值非正数。
     *
     * @param name 配置项名称
     * @param value 实际值
     * @return 统一异常
     */
    public static CaptchaException notPositive(String name, int value) {
        return new CaptchaException("captcha: " + name + " must be positive, but was: " + value);
    }

    /**
     * 集群存储依赖缺失。
     *
     * @param cause 链接错误
     * @return 统一异常
     */
    public static CaptchaException missingCache(Throwable cause) {
        return new CaptchaException("captcha: io.github.gollyhu:aifei-cache:1.1.0 is required for cluster store", cause);
    }

    /**
     * 参数为 null。
     *
     * @param method 方法名
     * @param name 参数名
     * @return 统一异常
     */
    public static CaptchaException nullArgument(String method, String name) {
        return new CaptchaException("captcha: " + method + " argument " + name + " must not be null");
    }

    /**
     * 存储系统级错误。
     *
     * @param cause 原异常
     * @return 统一异常
     */
    public static CaptchaException storageFailed(Throwable cause) {
        return new CaptchaException("captcha: storage failed, cause: " + detail(cause), cause);
    }

    /**
     * 图片生成失败。
     *
     * @param cause 原异常
     * @return 统一异常
     */
    public static CaptchaException imageFailed(Throwable cause) {
        return new CaptchaException("captcha: failed to generate image, cause: " + detail(cause), cause);
    }

    private static String detail(Throwable cause) {
        return cause == null ? "unknown" : String.valueOf(cause.getMessage());
    }
}
