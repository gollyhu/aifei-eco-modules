/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.qr;

/**
 * 二维码统一异常，收敛依赖缺失、参数非法、编码与解码失败。
 */
public final class QrException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造仅含消息的异常。
     *
     * @param message 可读错误消息
     */
    public QrException(String message) {
        super(message);
    }

    /**
     * 构造含消息与根因的异常。
     *
     * @param message 可读错误消息
     * @param cause 根因
     */
    public QrException(String message, Throwable cause) {
        super(message, cause);
    }
}
