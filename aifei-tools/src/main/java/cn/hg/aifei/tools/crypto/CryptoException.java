/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.crypto;

/**
 * crypto 统一异常，收敛参数非法、算法不可用与消息认证码计算失败。
 */
public final class CryptoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造仅含消息的异常。
     *
     * @param message 可读错误消息
     */
    public CryptoException(String message) {
        super(message);
    }

    /**
     * 构造含消息与根因的异常。
     *
     * @param message 可读错误消息
     * @param cause 根因
     */
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
