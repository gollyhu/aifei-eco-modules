/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha;

/**
 * 验证码统一异常，收敛配置非法、依赖缺失与存储系统级错误。
 *
 * <p>答案错误、token 过期、token 已消费、失败次数超限属业务失败，
 * {@link CaptchaKit#verify} 返回 false，不抛本异常。</p>
 */
public final class CaptchaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造仅含消息的异常。
     *
     * @param message 可读错误消息
     */
    public CaptchaException(String message) {
        super(message);
    }

    /**
     * 构造含消息与根因的异常。
     *
     * @param message 可读错误消息
     * @param cause 根因
     */
    public CaptchaException(String message, Throwable cause) {
        super(message, cause);
    }
}
