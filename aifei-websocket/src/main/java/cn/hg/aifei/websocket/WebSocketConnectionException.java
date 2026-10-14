/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

/**
 * 建连、传输与收发失败。
 *
 * <p>对底层传输库的异常一律包装为本类型后再交给调用方或回调，不外泄第三方异常类型。</p>
 */
public class WebSocketConnectionException extends WebSocketException {

    private static final long serialVersionUID = 1L;

    /**
     * 用消息构造异常。
     *
     * @param message 异常消息
     */
    public WebSocketConnectionException(String message) {
        super(message);
    }

    /**
     * 用消息与原因构造异常。
     *
     * @param message 异常消息
     * @param cause 原因
     */
    public WebSocketConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
