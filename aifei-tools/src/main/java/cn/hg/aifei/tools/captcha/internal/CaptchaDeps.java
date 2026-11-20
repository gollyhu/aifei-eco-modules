/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha.internal;

/**
 * aifei-cache 可选依赖探测（依赖探测唯一定义点之一）。
 */
public final class CaptchaDeps {

    private static final String CACHE_CLASS = "cn.hg.aifei.cache.CacheKit";

    private CaptchaDeps() {
    }

    /**
     * 确保共享缓存内核在类路径上可用，否则抛可读异常。
     *
     * @throws cn.hg.aifei.tools.captcha.CaptchaException 依赖缺失或链接失败
     */
    public static void requireCache() {
        try {
            Class.forName(CACHE_CLASS, false, CaptchaDeps.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw CaptchaErrors.missingCache(e);
        } catch (LinkageError e) {
            throw CaptchaErrors.missingCache(e);
        }
    }
}
