/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.internal;

import cn.hg.aifei.oss.OssException;

/**
 * 底层存储实现异常到 {@link OssException} 的翻译<b>唯一定义点</b>。
 *
 * <p>翻译后的异常消息只包含本模块自有的操作名、平台标识与对象路径，<b>不会</b>携带第三方类型名，也不回填底层
 * 异常的原始消息（其可能含厂商 SDK 类名）；底层异常一律经 {@code cause} 链保留，便于排查。已经是
 * {@link OssException} 的异常不再二次包装。</p>
 *
 * <p>本类属非承诺 API：因装配需要声明为 {@code public}，但不是公共 API，消费方不得引用。</p>
 */
public final class OssErrors {

    /**
     * 禁止创建工具类实例。
     */
    private OssErrors() {
    }

    /**
     * 翻译一个仅知操作名的失败。
     *
     * @param action 操作名，如 {@code build storage service}
     * @param cause 底层异常
     * @return 翻译后的 {@link OssException}
     */
    public static OssException translate(String action, Throwable cause) {
        if (cause instanceof OssException) {
            return (OssException) cause;
        }
        return new OssException("oss " + action + " failed", cause);
    }

    /**
     * 翻译一个带平台标识的失败。
     *
     * @param action 操作名，如 {@code upload}
     * @param platform 平台标识
     * @param cause 底层异常
     * @return 翻译后的 {@link OssException}
     */
    public static OssException translate(String action, String platform, Throwable cause) {
        if (cause instanceof OssException) {
            return (OssException) cause;
        }
        return new OssException("oss " + action + " failed, platform: " + platform, cause);
    }

    /**
     * 翻译一个带平台标识与对象路径的失败。
     *
     * @param action 操作名，如 {@code download}
     * @param platform 平台标识
     * @param path 对象路径
     * @param cause 底层异常
     * @return 翻译后的 {@link OssException}
     */
    public static OssException translate(String action, String platform, String path, Throwable cause) {
        if (cause instanceof OssException) {
            return (OssException) cause;
        }
        return new OssException("oss " + action + " failed, platform: " + platform + ", path: " + path, cause);
    }
}
