/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket;

/**
 * 运行态故障基类：连接失败、传输错误、编解码失败等运行期问题都用本类型族表达。
 *
 * <p>用户错误（参数为 {@code null}、取值范围越界、状态不符等）不与本类型族混用，分别抛
 * {@link IllegalArgumentException} 与 {@link IllegalStateException}。异常消息中不含任何第三方类型名。</p>
 */
public class WebSocketException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 用消息构造异常。
     *
     * @param message 异常消息
     */
    public WebSocketException(String message) {
        super(message);
    }

    /**
     * 用消息与原因构造异常。
     *
     * @param message 异常消息
     * @param cause 原因
     */
    public WebSocketException(String message, Throwable cause) {
        super(message, cause);
    }
}
