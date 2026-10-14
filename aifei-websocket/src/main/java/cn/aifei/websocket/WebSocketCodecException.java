/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket;

/**
 * 消息编解码失败。
 *
 * <p>由配置的编解码器实现抛出，或在编解码器抛错时由本模块包装后上浮；出现本异常时消息不发出。</p>
 */
public class WebSocketCodecException extends WebSocketException {

    private static final long serialVersionUID = 1L;

    /**
     * 用消息构造异常。
     *
     * @param message 异常消息
     */
    public WebSocketCodecException(String message) {
        super(message);
    }

    /**
     * 用消息与原因构造异常。
     *
     * @param message 异常消息
     * @param cause 原因
     */
    public WebSocketCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
